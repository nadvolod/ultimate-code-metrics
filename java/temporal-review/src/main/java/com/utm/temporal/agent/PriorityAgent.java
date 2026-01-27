package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;
import com.utm.temporal.util.PromptLoader;

import java.util.Arrays;
import java.util.List;

/**
 * Priority Agent - consolidates findings from other agents and ranks by severity.
 * Deduplicates overlapping concerns, orders by actionability, and groups related issues.
 */
public class PriorityAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public PriorityAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public PriorityAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public AgentResult prioritize(ReviewRequest request, List<AgentResult> agentResults) {
        try {
            // Build system prompt
            String systemPrompt = buildSystemPrompt();

            // Build user prompt with PR details and agent findings
            String agentFindings = formatAgentFindings(agentResults);
            String userPrompt = String.format(
                "PR Title: %s\n\nPR Description: %s\n\n=== AGENT FINDINGS ===\n%s",
                request.prTitle,
                request.prDescription,
                agentFindings
            );

            // Call LLM
            List<Message> messages = Arrays.asList(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            );

            LlmOptions options = new LlmOptions(
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"),
                0.2,
                "json_object"
            );

            String response = llmClient.chat(messages, options);

            // Parse JSON response into AgentResult
            return objectMapper.readValue(response, AgentResult.class);

        } catch (Exception e) {
            throw new RuntimeException("Priority Agent failed: " + e.getMessage(), e);
        }
    }

    private String formatAgentFindings(List<AgentResult> agentResults) {
        if (agentResults == null || agentResults.isEmpty()) {
            return "No findings from other agents.";
        }

        StringBuilder sb = new StringBuilder();
        for (AgentResult result : agentResults) {
            sb.append(String.format("\n[%s] Risk: %s, Recommendation: %s\n",
                result.agentName, result.riskLevel, result.recommendation));
            if (result.findings != null && !result.findings.isEmpty()) {
                for (String finding : result.findings) {
                    sb.append("  - ").append(finding).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return PromptLoader.loadPrompt("priority");
    }
}
