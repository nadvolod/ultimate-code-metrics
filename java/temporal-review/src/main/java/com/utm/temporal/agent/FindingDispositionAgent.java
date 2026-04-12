package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.FindingOutcome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compares review findings against follow-up changes to infer
 * which findings were addressed (ACCEPTED) vs ignored (DISMISSED).
 */
public class FindingDispositionAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public FindingDispositionAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public FindingDispositionAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<FindingOutcome> inferDispositions(List<FindingOutcome> findings, String followUpDiff) {
        try {
            String systemPrompt = buildSystemPrompt();

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("## Findings from the review:\n\n");
            for (int i = 0; i < findings.size(); i++) {
                FindingOutcome f = findings.get(i);
                userPrompt.append(String.format("%d. [%s] (%s risk) %s\n",
                        i + 1, f.agentName, f.riskLevel, f.finding));
            }
            userPrompt.append("\n## Follow-up diff (changes made after the review):\n\n");
            userPrompt.append(followUpDiff);

            List<Message> messages = Arrays.asList(
                    new Message("system", systemPrompt),
                    new Message("user", userPrompt.toString())
            );

            LlmOptions options = new LlmOptions(
                    System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"),
                    0.1,
                    "json_object"
            );

            String response = llmClient.chat(messages, options);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dispositions = root.path("dispositions");

            List<FindingOutcome> results = new ArrayList<>();
            for (int i = 0; i < findings.size(); i++) {
                FindingOutcome original = findings.get(i);
                FindingOutcome result = new FindingOutcome(
                        original.agentName, original.finding, original.riskLevel, "UNKNOWN");
                result.findingId = original.findingId;

                if (dispositions.has(i)) {
                    JsonNode d = dispositions.get(i);
                    result.disposition = d.path("disposition").asText("UNKNOWN");
                    result.evidence = d.path("evidence").asText("");
                }
                results.add(result);
            }
            return results;

        } catch (Exception e) {
            throw new RuntimeException("Finding disposition inference failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return "You are analyzing whether review findings were addressed in follow-up code changes.\n\n" +
               "For each finding, determine its disposition:\n" +
               "- ACCEPTED: The author made changes that address this finding\n" +
               "- DISMISSED: The finding was not addressed in the follow-up changes\n" +
               "- DEFERRED: The finding was partially addressed or acknowledged but not fully resolved\n" +
               "- UNKNOWN: Cannot determine from the available diff\n\n" +
               "Be specific in your evidence — cite what changed (or didn't) in the diff.\n\n" +
               "Respond ONLY with valid JSON matching this exact structure:\n" +
               "{\n" +
               "  \"dispositions\": [\n" +
               "    {\n" +
               "      \"findingIndex\": 0,\n" +
               "      \"disposition\": \"ACCEPTED|DISMISSED|DEFERRED|UNKNOWN\",\n" +
               "      \"evidence\": \"description of what changed or didn't change\"\n" +
               "    }\n" +
               "  ]\n" +
               "}";
    }
}
