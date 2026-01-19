package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.AgentResult;

import java.util.Arrays;
import java.util.List;

/**
 * Documentation Agent - validates that user-facing changes are documented.
 * Focuses on README, API docs, and public usage instructions.
 */
public class DocumentationAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public DocumentationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public DocumentationAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff) {
        try {
            String systemPrompt = buildSystemPrompt();

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
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"),
                0.2,
                "json_object"
            );

            String response = llmClient.chat(messages, options);

            return objectMapper.readValue(response, AgentResult.class);

        } catch (Exception e) {
            throw new RuntimeException("Documentation Agent failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return "You are a Documentation Reviewer analyzing pull request diffs.\n\n" +
               "Your task is to ensure user-facing changes are properly documented.\n\n" +
               "Check these areas:\n" +
               "1. **API & Public Interfaces**: New endpoints, request/response fields, CLI flags, or public methods must be documented.\n" +
               "2. **Configuration & Environment**: New config, feature flags, or env vars must be added to README or config docs.\n" +
               "3. **Behavior Changes**: Breaking changes or altered workflows must have upgrade notes or README updates.\n" +
               "4. **Docs-Only Changes**: If the diff is only docs/comments, APPROVE.\n\n" +
               "CRITICAL REQUIREMENTS:\n" +
               "- Findings must reference specific missing or updated docs in the diff\n" +
               "- Avoid generic feedback; cite concrete files, sections, or omissions\n" +
               "- If documentation is sufficient, explicitly say so\n\n" +
               "Risk Level Guidelines:\n" +
               "- LOW: Documentation is updated or not required\n" +
               "- MEDIUM: Minor doc gaps (README details missing)\n" +
               "- HIGH: Public API/config changes with no documentation updates\n\n" +
               "Recommendation Guidelines:\n" +
               "- APPROVE: Docs are updated or not required\n" +
               "- REQUEST_CHANGES: Documentation gaps for user-facing changes\n" +
               "- BLOCK: Breaking or critical API changes without documentation\n\n" +
               "Respond ONLY with valid JSON matching this exact structure:\n" +
               "{\n" +
               "  \"agentName\": \"Documentation\",\n" +
               "  \"riskLevel\": \"LOW|MEDIUM|HIGH\",\n" +
               "  \"recommendation\": \"APPROVE|REQUEST_CHANGES|BLOCK\",\n" +
               "  \"findings\": [\"specific finding 1\", \"specific finding 2\", ...]\n" +
               "}";
    }
}
