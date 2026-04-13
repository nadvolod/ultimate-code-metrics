package com.utm.temporal.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI LLM client implementation using OkHttp.
 * Supports DUMMY_MODE for development/testing without API costs.
 */
public class OpenAiLlmClient implements LlmClient {

    public static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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

        try {
            // Truncate user message content that exceeds the configured diff size limit
            List<Message> effectiveMessages = applyDiffSizeLimit(messages);

            // Build request JSON
            String requestBody = buildRequestBody(effectiveMessages, options);

            // Make HTTP call to OpenAI
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI API call failed: HTTP " + response.code() + " - " + response.message());
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (Exception e) {
            // No retry logic - fail fast!
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }
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
     * Truncates {@code content} to at most {@code maxChars} characters and appends a
     * notice explaining the truncation. Visible for testing.
     */
    static String truncateDiff(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + String.format(TRUNCATION_NOTICE, maxChars);
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
