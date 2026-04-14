package com.utm.temporal.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final boolean dummyMode;

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

        if (!dummyMode && apiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required when DUMMY_MODE is not enabled");
        }
    }

    @Override
    public String chat(List<Message> messages, LlmOptions options) {
        if (dummyMode) {
            return getDummyResponse(messages);
        }

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
                String requestBody = buildRequestBody(messages, options);

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
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");

        if (content.isMissingNode()) {
            throw new IOException("Invalid OpenAI API response: missing 'choices[0].message.content'");
        }

        return content.asText();
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

        if (systemMessage.contains("code quality")) {
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
