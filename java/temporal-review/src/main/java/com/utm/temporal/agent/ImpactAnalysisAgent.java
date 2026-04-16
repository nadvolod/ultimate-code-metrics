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
 * Impact Analysis Agent - identifies the blast radius of code changes for QA teams.
 * Analyzes which other code areas are affected by the current change.
 */
public class ImpactAnalysisAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ImpactAnalysisAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public ImpactAnalysisAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff, String learningContext) {
        try {
            String systemPrompt = buildSystemPrompt() + (learningContext != null ? learningContext : "");

            String userPrompt = String.format(
                "PR Title: %s\n\nPR Description: %s\n\nDiff:\n%s",
                prTitle,
                prDescription,
                diff
            );

            List<Message> messages = Arrays.asList(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            );

            LlmOptions options = new LlmOptions(
                System.getenv().getOrDefault("OPENAI_MODEL", OpenAiLlmClient.DEFAULT_MODEL),
                0.2,
                "json_object"
            );

            String response = llmClient.chat(messages, options);

            AgentResult result = objectMapper.readValue(response, AgentResult.class);
            result.promptTokens = llmClient.getLastPromptTokens();
            result.completionTokens = llmClient.getLastCompletionTokens();
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Impact Analysis Agent failed: " + e.getMessage(), e);
        }
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff) {
        return analyze(prTitle, prDescription, diff, null);
    }

    private String buildSystemPrompt() {
        return PromptLoader.loadPrompt("impact-analysis");
    }
}
