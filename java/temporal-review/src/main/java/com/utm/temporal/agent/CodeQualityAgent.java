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
 * Code Quality Agent - evaluates code quality against predefined criteria.
 * Uses LLM to analyze diff and provide specific, actionable feedback.
 */
public class CodeQualityAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public CodeQualityAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public CodeQualityAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff, String learningContext) {
        try {
            // Build system prompt with criteria + optional learning context
            String systemPrompt = buildSystemPrompt() + (learningContext != null ? learningContext : "");

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
                System.getenv().getOrDefault("OPENAI_MODEL", OpenAiLlmClient.DEFAULT_MODEL),
                0.2,  // Low temperature for consistent, focused analysis
                "json_object"
            );

            String response = llmClient.chat(messages, options);

            // Parse JSON response into AgentResult
            AgentResult result = objectMapper.readValue(response, AgentResult.class);
            result.promptTokens = llmClient.getLastPromptTokens();
            result.completionTokens = llmClient.getLastCompletionTokens();
            return result;

        } catch (Exception e) {
            // Fail fast - no retry logic!
            throw new RuntimeException("Code Quality Agent failed: " + e.getMessage(), e);
        }
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff) {
        return analyze(prTitle, prDescription, diff, null);
    }

    private String buildSystemPrompt() {
        return PromptLoader.loadPrompt("code-quality");
    }
}
