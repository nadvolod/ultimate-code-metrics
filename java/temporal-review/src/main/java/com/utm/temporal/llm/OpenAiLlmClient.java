package com.utm.temporal.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI LLM client implementation using OkHttp.
 * Supports DUMMY_MODE for development/testing without API costs.
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiLlmClient.class);

    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // In-client retry handles transient OpenAI API errors (429, 5xx) before they bubble up
    // to Temporal's activity-level retry, which would re-execute the entire activity.
    private static final int MAX_RETRIES = 3;
    private static final int TOTAL_ATTEMPTS = MAX_RETRIES + 1;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 30000L;

    /** Default maximum number of characters allowed in a user message (diff) before truncation. */
    public static final int DEFAULT_MAX_DIFF_CHARS = 100_000;
    private static final String TRUNCATION_NOTICE =
            "\n\n[TRUNCATED: Diff exceeded the configured size limit. Analysis is based on the first %d characters only.]";


    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final boolean dummyMode;
    private final int maxDiffChars;

    // Token usage from the last API call
    private int lastPromptTokens;
    private int lastCompletionTokens;

    public OpenAiLlmClient() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Read environment variables
        this.apiKey = System.getenv().getOrDefault("OPENAI_API_KEY", "");
        this.baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", DEFAULT_BASE_URL);
        this.dummyMode = "true".equalsIgnoreCase(System.getenv().getOrDefault("DUMMY_MODE", "false"));

        String maxDiffCharsEnv = System.getenv().getOrDefault("MAX_DIFF_CHARS", String.valueOf(DEFAULT_MAX_DIFF_CHARS));
        try {
            this.maxDiffChars = Integer.parseInt(maxDiffCharsEnv);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("MAX_DIFF_CHARS environment variable must be a valid integer, got: " + maxDiffCharsEnv);
        }
        if (this.maxDiffChars <= 0) {
            throw new IllegalStateException("MAX_DIFF_CHARS environment variable must be a positive integer, got: " + this.maxDiffChars);
        }

        if (!dummyMode && apiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required when DUMMY_MODE is not enabled");
        }
    }

    /** Package-private constructor for unit testing with a custom diff size limit. */
    OpenAiLlmClient(int maxDiffChars) {
        if (maxDiffChars <= 0) {
            throw new IllegalArgumentException("maxDiffChars must be a positive integer, got: " + maxDiffChars);
        }
        this.objectMapper = new ObjectMapper();
        this.httpClient = null;
        this.apiKey = "";
        this.baseUrl = DEFAULT_BASE_URL;
        this.dummyMode = true;
        this.maxDiffChars = maxDiffChars;
    }

    @Override
    public String chat(List<Message> messages, LlmOptions options) {
        if (dummyMode) {
            return getDummyResponse(messages);
        }

        // Truncate user message content that exceeds the configured diff size limit
        List<Message> effectiveMessages = applyDiffSizeLimit(messages);

        IOException lastException = null;


        for (int attempt = 0; attempt < TOTAL_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                long backoffMs = Math.min(INITIAL_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS); // Exponential backoff: INITIAL_BACKOFF_MS * 2^(attempt-1), capped at MAX_BACKOFF_MS
                logger.warn("OpenAI API call failed (attempt {}), retrying in {}ms...", attempt + 1, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry OpenAI API call", ie);
                }
            }

            try {
                String requestBody = buildRequestBody(effectiveMessages, options);

                Request request = new Request.Builder()
                        .url(baseUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody, JSON))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    int statusCode = response.code();

                    if (response.isSuccessful()) {
                        if (response.body() == null) {
                            throw new RuntimeException(
                                    "OpenAI API returned a successful response (HTTP " + statusCode +
                                    ") but the response body was null");
                        }
                        String responseBody = response.body().string();
                        return parseResponse(responseBody);
                    }

                    // 4xx errors (except 429 rate limit) are not retryable
                    if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
                        throw new RuntimeException(
                                "OpenAI API call failed with non-retryable error: HTTP " + statusCode +
                                " - " + response.message() + " [error_code=CLIENT_ERROR]");
                    }

                    // 429 (rate limit) and 5xx errors are retryable
                    lastException = new IOException(
                            "OpenAI API call failed: HTTP " + statusCode + " - " + response.message() +
                            " [error_code=" + (statusCode == 429 ? "RATE_LIMIT" : "SERVER_ERROR") + "]");
                }
            } catch (RuntimeException e) {
                // Non-retryable errors bubble up immediately
                throw e;
            } catch (IOException e) {
                // Network/IO errors are retryable
                lastException = e;
                logger.warn("OpenAI API network error on attempt {}: {}", attempt + 1, e.getMessage());
            }
        }

        // All retries exhausted
        logger.error("OpenAI API call failed after {} attempts. Last error: {}", TOTAL_ATTEMPTS,
                lastException != null ? lastException.getMessage() : "unknown");
        throw new RuntimeException(
                "OpenAI API call failed after " + TOTAL_ATTEMPTS + " attempts [error_code=SERVICE_UNAVAILABLE]: " +
                (lastException != null ? lastException.getMessage() : "unknown error"),
                lastException);
    }

    /**
     * Returns a copy of the message list with user message content truncated to
     * {@code maxDiffChars} characters. A truncation notice is appended so the LLM
     * knows the input was cut off.
     */
    List<Message> applyDiffSizeLimit(List<Message> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            if ("user".equals(msg.role) && msg.content != null && msg.content.length() > maxDiffChars) {
                String truncated = truncateDiff(msg.content, maxDiffChars);
                result.add(new Message(msg.role, truncated));
            } else {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * Truncates {@code content} so that the total output (prefix + notice) is at most
     * {@code maxChars} characters. Visible for testing.
     */
    static String truncateDiff(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        String notice = String.format(TRUNCATION_NOTICE, maxChars);
        if (notice.length() >= maxChars) {
            return content.substring(0, maxChars);
        }
        int prefixLen = maxChars - notice.length();
        return content.substring(0, prefixLen) + notice;
    }

    private String buildRequestBody(List<Message> messages, LlmOptions options) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(options.model != null ? options.model : DEFAULT_MODEL).append("\",");
        json.append("\"temperature\":").append(options.temperature).append(",");

        if ("json_object".equals(options.responseFormat)) {
            json.append("\"response_format\":{\"type\":\"json_object\"},");
        }

        json.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            json.append("{\"role\":\"").append(msg.role).append("\",");
            json.append("\"content\":").append(objectMapper.writeValueAsString(msg.content)).append("}");
            if (i < messages.size() - 1) {
                json.append(",");
            }
        }
        json.append("]}");

        return json.toString();
    }

    private String parseResponse(String responseBody) throws IOException {
        // Reset counters to avoid leaking stale values from a previous call
        this.lastPromptTokens = 0;
        this.lastCompletionTokens = 0;

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");

        if (content.isMissingNode()) {
            throw new IOException("Invalid OpenAI API response: missing 'choices[0].message.content'");
        }

        // Capture token usage from response
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            this.lastPromptTokens = usage.path("prompt_tokens").asInt(0);
            this.lastCompletionTokens = usage.path("completion_tokens").asInt(0);
        }

        return content.asText();
    }

    @Override
    public int getLastPromptTokens() {
        return lastPromptTokens;
    }

    @Override
    public int getLastCompletionTokens() {
        return lastCompletionTokens;
    }

    /**
     * Returns deterministic canned responses for development/testing.
     */
    private String getDummyResponse(List<Message> messages) {
        // Determine which agent is calling based on the system message
        String systemMessage = messages.stream()
                .filter(m -> "system".equals(m.role))
                .map(m -> m.content.toLowerCase())
                .findFirst()
                .orElse("");

        if (systemMessage.contains("product owner") || systemMessage.contains("triages and prioritizes")) {
            return "{\n" +
                   "  \"triageDate\": \"2026-03-30T00:00:00Z\",\n" +
                   "  \"totalIssues\": 5,\n" +
                   "  \"summary\": \"Backlog has a mix of security, testing, and feature work. Security and testing gaps are highest priority.\",\n" +
                   "  \"issues\": [\n" +
                   "    {\"number\": 17, \"title\": \"Mitigate script injection\", \"priority\": \"P0\", \"category\": \"security\", \"effort\": \"S\", \"blockedBy\": [], \"rationale\": \"Active security vulnerability in CI pipeline\"},\n" +
                   "    {\"number\": 16, \"title\": \"Address HIGH severity vulnerabilities\", \"priority\": \"P1\", \"category\": \"security\", \"effort\": \"M\", \"blockedBy\": [], \"rationale\": \"Known CVEs in transitive dependencies\"},\n" +
                   "    {\"number\": 4, \"title\": \"Guard against unexpected status values\", \"priority\": \"P2\", \"category\": \"code-quality\", \"effort\": \"S\", \"blockedBy\": [], \"rationale\": \"Quick win - prevents runtime crashes\"},\n" +
                   "    {\"number\": 3, \"title\": \"Update site metadata\", \"priority\": \"P3\", \"category\": \"UX\", \"effort\": \"S\", \"blockedBy\": [], \"rationale\": \"Cosmetic but aligns branding with actual functionality\"},\n" +
                   "    {\"number\": 10, \"title\": \"Unused testSummary parameter\", \"priority\": \"P3\", \"category\": \"code-quality\", \"effort\": \"S\", \"blockedBy\": [], \"rationale\": \"Dead code removal\"}\n" +
                   "  ],\n" +
                   "  \"recommendedOrder\": [17, 16, 4, 10, 3],\n" +
                   "  \"quickWins\": [17, 4, 10, 3],\n" +
                   "  \"riskAreas\": [\"CI/CD pipeline security\", \"Dependency management\", \"Frontend error handling\"]\n" +
                   "}";
        } else if (systemMessage.contains("code quality")) {
            return "{\n" +
                   "  \"agentName\": \"Code Quality\",\n" +
                   "  \"riskLevel\": \"LOW\",\n" +
                   "  \"recommendation\": \"APPROVE\",\n" +
                   "  \"findings\": [\n" +
                   "    \"Function names are clear and descriptive\",\n" +
                   "    \"Code follows single responsibility principle\",\n" +
                   "    \"Error handling is present and appropriate\"\n" +
                   "  ]\n" +
                   "}";
        } else if (systemMessage.contains("complexity")) {
            return "{\n" +
                   "  \"agentName\": \"Complexity\",\n" +
                   "  \"riskLevel\": \"LOW\",\n" +
                   "  \"recommendation\": \"APPROVE\",\n" +
                   "  \"findings\": [\n" +
                   "    \"Cyclomatic Complexity: 12\",\n" +
                   "    \"Cognitive Complexity: 18\",\n" +
                   "    \"Primary driver: nested conditionals in request validation\"\n" +
                   "  ]\n" +
                   "}";
        } else if (systemMessage.contains("test quality")) {
            return "{\n" +
                   "  \"agentName\": \"Test Quality\",\n" +
                   "  \"riskLevel\": \"LOW\",\n" +
                   "  \"recommendation\": \"APPROVE\",\n" +
                   "  \"findings\": [\n" +
                   "    \"Tests cover main functionality\",\n" +
                   "    \"Edge cases are tested\",\n" +
                   "    \"Test names are descriptive\"\n" +
                   "  ]\n" +
                   "}";
        } else if (systemMessage.contains("security")) {
            return "{\n" +
                   "  \"agentName\": \"Security\",\n" +
                   "  \"riskLevel\": \"LOW\",\n" +
                   "  \"recommendation\": \"APPROVE\",\n" +
                   "  \"findings\": [\n" +
                   "    \"No hardcoded secrets detected\",\n" +
                   "    \"Input validation is present\",\n" +
                   "    \"No SQL injection vulnerabilities found\"\n" +
                   "  ]\n" +
                   "}";
        }

        // Default response if agent type can't be determined
        return "{\n" +
               "  \"agentName\": \"Unknown\",\n" +
               "  \"riskLevel\": \"MEDIUM\",\n" +
               "  \"recommendation\": \"REQUEST_CHANGES\",\n" +
               "  \"findings\": [\"Unable to determine agent type - dummy mode\"]\n" +
               "}";
    }
}
