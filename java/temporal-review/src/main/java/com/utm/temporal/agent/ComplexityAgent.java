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
 * Complexity Agent - estimates cyclomatic and cognitive complexity in diffs.
 */
public class ComplexityAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ComplexityAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public ComplexityAgent() {
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
            throw new RuntimeException("Complexity Agent failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return "You are a Complexity Reviewer analyzing pull request diffs.\n\n" +
               "Estimate quantitative cyclomatic and cognitive complexity for the changed code.\n\n" +
               "Guidelines:\n" +
               "- Cyclomatic Complexity: count of decision paths (if/else, switch cases, loops, boolean operators)\n" +
               "- Cognitive Complexity: mental effort including nesting, recursion, and logical branching\n" +
               "- Base your metrics on the diff, not the whole codebase\n\n" +
               "Risk Level Guidelines:\n" +
               "- LOW: Both metrics <= 10\n" +
               "- MEDIUM: Any metric between 11-20\n" +
               "- HIGH: Any metric > 20\n\n" +
               "Recommendation Guidelines:\n" +
               "- APPROVE: LOW complexity\n" +
               "- REQUEST_CHANGES: MEDIUM complexity with refactor suggestions\n" +
               "- BLOCK: HIGH complexity that risks maintainability\n\n" +
               "CRITICAL REQUIREMENTS:\n" +
               "- Include numeric metrics in findings\n" +
               "- Provide Cyclomatic and Cognitive complexity as separate findings\n" +
               "- Add one brief finding explaining the main complexity driver\n\n" +
               "Respond ONLY with valid JSON matching this exact structure:\n" +
               "{\n" +
               "  \"agentName\": \"Complexity\",\n" +
               "  \"riskLevel\": \"LOW|MEDIUM|HIGH\",\n" +
               "  \"recommendation\": \"APPROVE|REQUEST_CHANGES|BLOCK\",\n" +
               "  \"findings\": [\n" +
               "    \"Cyclomatic Complexity: 8\",\n" +
               "    \"Cognitive Complexity: 12\",\n" +
               "    \"Primary driver: nested if-else statements\"\n" +
               "  ]\n" +
               "}";
    }
}
