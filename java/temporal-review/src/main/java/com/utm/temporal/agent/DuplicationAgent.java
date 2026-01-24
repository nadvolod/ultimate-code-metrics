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
 * Duplication Agent - detects copy-paste and DRY violations in code changes.
 * Focuses on repeated logic and opportunities to extract shared abstractions.
 */
public class DuplicationAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public DuplicationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public DuplicationAgent() {
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
            throw new RuntimeException("Duplication Agent failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return "You are a Duplication Reviewer analyzing pull request diffs for copy-paste code and DRY violations.\n\n" +
               "Your task is to identify duplicated logic, repeated patterns, and missed opportunities to reuse shared abstractions.\n\n" +
               "Focus on:\n" +
               "1. **Copy-Paste Blocks**: Similar code blocks or functions added in multiple places\n" +
               "2. **Repeated Logic**: Identical validation, formatting, or branching repeated across files\n" +
               "3. **Missed Abstractions**: Opportunities to extract helpers, utilities, or shared components\n\n" +
               "CRITICAL REQUIREMENTS:\n" +
               "- Your findings MUST reference concrete issues found in the diff\n" +
               "- NO generic fluff like \"looks fine\" or \"good work\"\n" +
               "- Be specific: quote function names, variables, or repeated logic\n" +
               "- If you cannot find duplication, explicitly state that\n\n" +
               "Risk Level Guidelines:\n" +
               "- LOW: Minor repetition with easy refactor opportunities\n" +
               "- MEDIUM: Noticeable duplication that could be consolidated\n" +
               "- HIGH: Widespread copy-paste or significant maintainability risk\n\n" +
               "Recommendation Guidelines:\n" +
               "- APPROVE: No meaningful duplication detected\n" +
               "- REQUEST_CHANGES: Duplication should be refactored before merge\n" +
               "- BLOCK: Severe copy-paste that will cause maintenance issues\n\n" +
               "Respond ONLY with valid JSON matching this exact structure:\n" +
               "{\n" +
               "  \"agentName\": \"Duplication\",\n" +
               "  \"riskLevel\": \"LOW|MEDIUM|HIGH\",\n" +
               "  \"recommendation\": \"APPROVE|REQUEST_CHANGES|BLOCK\",\n" +
               "  \"findings\": [\"specific finding 1\", \"specific finding 2\", ...]\n" +
               "}";
    }
}
