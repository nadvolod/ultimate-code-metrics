package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.util.PromptLoader;

import java.util.Arrays;
import java.util.List;

/**
 * Security Agent - identifies practical application security issues.
 * Focuses on secrets, authentication/authorization, and injection vulnerabilities.
 */
public class SecurityAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SecurityAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public SecurityAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff) {
        try {
            // Build system prompt with security rules
            String systemPrompt = buildSystemPrompt();

            // Build user prompt with PR details
            String userPrompt = String.format(
                "PR Title: %s\n\nPR Description: %s\n\nDiff:\n%s",
                prTitle,
                prDescription,
                diff
            );

            // Call LLM
            List<Message> messages = Arrays.asList(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            );

            LlmOptions options = new LlmOptions(
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"),
                0.1,  // Very low temperature for consistent security analysis
                "json_object"
            );

            String response = llmClient.chat(messages, options);

            // Parse JSON response into AgentResult
            return objectMapper.readValue(response, AgentResult.class);

        } catch (Exception e) {
            // Fail fast - no retry logic!
            throw new RuntimeException("Security Agent failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return PromptLoader.loadPrompt("security");
    }
}
