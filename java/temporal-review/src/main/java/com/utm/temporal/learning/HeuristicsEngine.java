package com.utm.temporal.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies approved heuristics and severity calibrations to AgentResults.
 * This is deterministic post-processing — no LLM calls.
 *
 * The engine loads learned rules from the database and applies them to each
 * agent's output, filtering false positives and calibrating severity levels
 * based on historical review outcome data.
 */
public class HeuristicsEngine {
    private final List<LearnedHeuristic> heuristics;
    private final List<SeverityCalibration> calibrations;
    private final ObjectMapper objectMapper;

    public HeuristicsEngine(LearningInsights insights) {
        this.heuristics = insights != null && insights.activeHeuristics != null
                ? insights.activeHeuristics : new ArrayList<>();
        this.calibrations = insights != null && insights.activeCalibrations != null
                ? insights.activeCalibrations : new ArrayList<>();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Apply heuristics and calibrations to an AgentResult.
     * Returns a new AgentResult (does not modify the original).
     */
    public AgentResult apply(AgentResult result, String diff) {
        AgentResult adjusted = new AgentResult();
        adjusted.agentName = result.agentName;
        adjusted.riskLevel = result.riskLevel;
        adjusted.recommendation = result.recommendation;
        adjusted.findings = new ArrayList<>(result.findings != null ? result.findings : new ArrayList<>());

        // Apply PATH_OVERRIDE heuristics — remove findings for excluded paths
        for (LearnedHeuristic h : heuristics) {
            if (!"PATH_OVERRIDE".equals(h.heuristicType)) continue;
            if (h.agentName != null && !h.agentName.equals(result.agentName)) continue;

            String pathPattern = extractPathPattern(h.rule);
            if (pathPattern != null && diffMatchesPath(diff, pathPattern)) {
                adjusted.findings = adjusted.findings.stream()
                        .filter(f -> !findingMatchesHeuristic(f, h))
                        .collect(Collectors.toList());
            }
        }

        // Apply SKIP_CATEGORY heuristics — remove findings by category keyword
        for (LearnedHeuristic h : heuristics) {
            if (!"SKIP_CATEGORY".equals(h.heuristicType)) continue;
            if (h.agentName != null && !h.agentName.equals(result.agentName)) continue;

            String category = extractCategory(h.rule);
            if (category != null) {
                adjusted.findings = adjusted.findings.stream()
                        .filter(f -> !f.toLowerCase().contains(category.toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // Apply severity calibrations
        for (SeverityCalibration cal : calibrations) {
            if (!cal.agentName.equals(result.agentName)) continue;
            if (cal.originalLevel.equals(adjusted.riskLevel) && cal.confidence >= 0.7) {
                adjusted.riskLevel = cal.calibratedLevel;
            }
        }

        // If all findings were removed, upgrade recommendation to APPROVE
        if (adjusted.findings.isEmpty() && !"APPROVE".equals(adjusted.recommendation)) {
            adjusted.recommendation = "APPROVE";
            adjusted.riskLevel = "LOW";
        }

        return adjusted;
    }

    /**
     * Get prompt patches for a specific agent from the insights.
     */
    public static String buildPromptContext(List<PromptPatch> patches, String agentName) {
        List<PromptPatch> relevant = patches.stream()
                .filter(p -> agentName.equals(p.agentName))
                .collect(Collectors.toList());

        if (relevant.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== LEARNED CONTEXT (from approved learning) ===\n\n");
        for (PromptPatch patch : relevant) {
            sb.append("- ").append(patch.patchContent).append("\n");
        }
        return sb.toString();
    }

    private String extractPathPattern(String ruleJson) {
        try {
            JsonNode rule = objectMapper.readTree(ruleJson);
            return rule.path("path").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCategory(String ruleJson) {
        try {
            JsonNode rule = objectMapper.readTree(ruleJson);
            return rule.path("category").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean diffMatchesPath(String diff, String pathPattern) {
        if (diff == null || pathPattern == null) return false;
        // Simple check: does the diff contain file paths matching the pattern
        String normalized = pathPattern.replace("**", "").replace("*", "");
        return diff.contains(normalized);
    }

    private boolean findingMatchesHeuristic(String finding, LearnedHeuristic heuristic) {
        // Simple keyword match — the heuristic description tells us what to skip
        String desc = heuristic.description.toLowerCase();
        String findingLower = finding.toLowerCase();

        // Extract action keywords from the heuristic
        try {
            JsonNode rule = objectMapper.readTree(heuristic.rule);
            String action = rule.path("action").asText("");
            if (!action.isEmpty() && findingLower.contains(action.toLowerCase())) {
                return true;
            }
        } catch (Exception e) {
            // Fall through to description match
        }

        return false;
    }
}
