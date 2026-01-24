package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

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
        return "You are a Priority Agent that consolidates and ranks findings from multiple code review agents.\n\n" +
               "Your tasks:\n" +
               "1. **Consolidate** findings from Code Quality, Test Quality, and Security agents\n" +
               "2. **Rank by severity**: P0-Critical (blockers), P1-High, P2-Medium, P3-Low\n" +
               "3. **Deduplicate** overlapping concerns across agents\n" +
               "4. **Order by actionability**: quick wins first, then larger refactors\n" +
               "5. **Group related issues**: e.g., all auth-related findings together\n\n" +
               "Priority Levels:\n" +
               "- P0 (Critical): Security vulnerabilities, auth bypasses, data corruption risks - MUST fix before merge\n" +
               "- P1 (High): Missing critical tests, significant code quality issues affecting maintainability\n" +
               "- P2 (Medium): Code style issues, minor refactoring opportunities, documentation gaps\n" +
               "- P3 (Low): Nice-to-have improvements, minor suggestions\n\n" +
               "Risk Level Guidelines (for overall assessment):\n" +
               "- HIGH: Has P0 or multiple P1 issues\n" +
               "- MEDIUM: Has P1 or multiple P2 issues, no P0\n" +
               "- LOW: Only P2/P3 issues or no issues\n\n" +
               "Recommendation Logic:\n" +
               "- BLOCK: Any P0 issue exists\n" +
               "- REQUEST_CHANGES: P1 issues exist but no P0\n" +
               "- APPROVE: Only P2/P3 issues or no issues\n\n" +
               "IMPORTANT:\n" +
               "- Each finding should start with priority level: \"P0:\", \"P1:\", \"P2:\", or \"P3:\"\n" +
               "- Include the source agent in brackets: [Security], [CodeQuality], [TestQuality]\n" +
               "- Add brief actionable guidance after each finding\n" +
               "- If multiple agents report similar issues, consolidate into one finding\n\n" +
               "Respond ONLY with valid JSON matching this exact structure:\n" +
               "{\n" +
               "  \"agentName\": \"Priority\",\n" +
               "  \"riskLevel\": \"LOW|MEDIUM|HIGH\",\n" +
               "  \"recommendation\": \"APPROVE|REQUEST_CHANGES|BLOCK\",\n" +
               "  \"findings\": [\n" +
               "    \"P0: Description [Source] - Action required\",\n" +
               "    \"P1: Description [Source] - Action required\",\n" +
               "    ...\n" +
               "  ]\n" +
               "}";
    }
}
