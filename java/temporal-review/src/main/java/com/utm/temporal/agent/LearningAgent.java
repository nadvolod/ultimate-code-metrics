package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.*;

import java.util.*;

/**
 * Analyzes accumulated review outcomes and proposes learning improvements.
 * Quantitative stats are computed from data; semantic pattern analysis uses the LLM.
 */
public class LearningAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LearningAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public LearningAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyzes findings and their outcomes to produce learning proposals.
     * Returns a LearningProposals object containing heuristics, prompt patches, and calibrations.
     */
    public LearningProposals analyze(String repository,
                                      Map<String, AgentAccuracy> accuracyStats,
                                      List<FindingOutcome> allFindings,
                                      int totalReviews) {
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(repository, accuracyStats, allFindings, totalReviews);

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
            return parseResponse(repository, response);

        } catch (Exception e) {
            throw new RuntimeException("Learning analysis failed: " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(String repository,
                                    Map<String, AgentAccuracy> accuracyStats,
                                    List<FindingOutcome> allFindings,
                                    int totalReviews) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(repository).append("\n");
        sb.append("Total reviews analyzed: ").append(totalReviews).append("\n\n");

        // Agent accuracy stats
        sb.append("## Agent Accuracy\n\n");
        for (Map.Entry<String, AgentAccuracy> entry : accuracyStats.entrySet()) {
            AgentAccuracy a = entry.getValue();
            sb.append(String.format("- %s: %d findings, %d accepted, %d dismissed, %.0f%% precision\n",
                    a.agentName, a.totalFindings, a.acceptedFindings, a.dismissedFindings,
                    a.precisionRate * 100));
        }

        // Group findings by disposition
        sb.append("\n## Dismissed Findings (potential false positives)\n\n");
        int dismissedCount = 0;
        for (FindingOutcome f : allFindings) {
            if ("DISMISSED".equals(f.disposition) && dismissedCount < 50) {
                sb.append(String.format("- [%s] (%s risk) %s\n", f.agentName, f.riskLevel, f.finding));
                dismissedCount++;
            }
        }

        sb.append("\n## Accepted Findings (high-signal)\n\n");
        int acceptedCount = 0;
        for (FindingOutcome f : allFindings) {
            if ("ACCEPTED".equals(f.disposition) && acceptedCount < 50) {
                sb.append(String.format("- [%s] (%s risk) %s\n", f.agentName, f.riskLevel, f.finding));
                acceptedCount++;
            }
        }

        return sb.toString();
    }

    private LearningProposals parseResponse(String repository, String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        LearningProposals proposals = new LearningProposals();

        // Parse heuristics
        JsonNode heuristicsNode = root.path("heuristics");
        if (heuristicsNode.isArray()) {
            for (JsonNode h : heuristicsNode) {
                LearnedHeuristic heuristic = new LearnedHeuristic();
                heuristic.repository = repository;
                heuristic.agentName = h.path("agentName").asText(null);
                heuristic.heuristicType = h.path("type").asText("CUSTOM");
                heuristic.rule = objectMapper.writeValueAsString(h.path("rule"));
                heuristic.description = h.path("description").asText();
                heuristic.evidence = h.path("evidence").asText();
                proposals.heuristics.add(heuristic);
            }
        }

        // Parse prompt patches
        JsonNode patchesNode = root.path("promptPatches");
        if (patchesNode.isArray()) {
            for (JsonNode p : patchesNode) {
                PromptPatch patch = new PromptPatch();
                patch.repository = repository;
                patch.agentName = p.path("agentName").asText();
                patch.patchType = p.path("type").asText("ADD_INSTRUCTION");
                patch.description = p.path("description").asText();
                patch.patchContent = p.path("content").asText();
                patch.evidence = p.path("evidence").asText();
                proposals.promptPatches.add(patch);
            }
        }

        // Parse severity calibrations
        JsonNode calibrationsNode = root.path("severityCalibrations");
        if (calibrationsNode.isArray()) {
            for (JsonNode c : calibrationsNode) {
                SeverityCalibration cal = new SeverityCalibration();
                cal.repository = repository;
                cal.agentName = c.path("agentName").asText();
                cal.category = c.path("category").asText();
                cal.originalLevel = c.path("originalLevel").asText();
                cal.calibratedLevel = c.path("calibratedLevel").asText();
                cal.confidence = c.path("confidence").asDouble(0.5);
                cal.sampleSize = c.path("sampleSize").asInt(0);
                proposals.calibrations.add(cal);
            }
        }

        return proposals;
    }

    private String buildSystemPrompt() {
        return "You are a Review Outcome Learning Agent analyzing patterns in PR review outcomes.\n\n" +
               "You will receive:\n" +
               "1. Per-agent accuracy statistics (accepted vs dismissed findings)\n" +
               "2. Lists of dismissed findings (potential false positives)\n" +
               "3. Lists of accepted findings (high-signal patterns)\n\n" +
               "Your job is to identify patterns and propose improvements:\n\n" +
               "## Heuristics\n" +
               "Structured rules to filter or adjust findings. Types:\n" +
               "- PATH_OVERRIDE: skip checks for specific file paths (e.g., test files)\n" +
               "- SKIP_CATEGORY: stop flagging a category that's always dismissed\n" +
               "- SEVERITY_ADJUST: change risk level for specific contexts\n\n" +
               "## Prompt Patches\n" +
               "Text to add or modify in agent system prompts. Types:\n" +
               "- ADD_INSTRUCTION: new guidance based on repo conventions\n" +
               "- MODIFY_CRITERIA: adjust evaluation criteria based on what matters\n\n" +
               "## Severity Calibrations\n" +
               "Risk level adjustments when data shows agents consistently over- or under-rate severity.\n\n" +
               "IMPORTANT:\n" +
               "- Only propose changes backed by clear evidence (high dismissal rate, consistent patterns)\n" +
               "- Be conservative — propose fewer, higher-confidence changes\n" +
               "- Include the evidence (dismissal rate, sample size) for each proposal\n" +
               "- A dismissal rate below 60% is not enough to propose a change\n\n" +
               "Respond ONLY with valid JSON matching this structure:\n" +
               "{\n" +
               "  \"heuristics\": [\n" +
               "    {\"agentName\": \"...\", \"type\": \"PATH_OVERRIDE|SKIP_CATEGORY|SEVERITY_ADJUST\", " +
               "\"rule\": {\"path\": \"...\", \"action\": \"...\"}, \"description\": \"...\", \"evidence\": \"...\"}\n" +
               "  ],\n" +
               "  \"promptPatches\": [\n" +
               "    {\"agentName\": \"...\", \"type\": \"ADD_INSTRUCTION|MODIFY_CRITERIA\", " +
               "\"description\": \"...\", \"content\": \"...\", \"evidence\": \"...\"}\n" +
               "  ],\n" +
               "  \"severityCalibrations\": [\n" +
               "    {\"agentName\": \"...\", \"category\": \"...\", \"originalLevel\": \"...\", " +
               "\"calibratedLevel\": \"...\", \"confidence\": 0.0, \"sampleSize\": 0}\n" +
               "  ]\n" +
               "}";
    }

    /** Container for all proposals from a learning run */
    public static class LearningProposals {
        public List<LearnedHeuristic> heuristics = new ArrayList<>();
        public List<PromptPatch> promptPatches = new ArrayList<>();
        public List<SeverityCalibration> calibrations = new ArrayList<>();
    }
}
