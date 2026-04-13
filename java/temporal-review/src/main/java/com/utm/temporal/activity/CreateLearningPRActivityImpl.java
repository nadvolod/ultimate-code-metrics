package com.utm.temporal.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.github.GitHubClient;
import com.utm.temporal.model.*;

import java.util.List;
import java.util.Map;

public class CreateLearningPRActivityImpl implements CreateLearningPRActivity {
    private final DatabaseClient databaseClient;
    private final GitHubClient gitHubClient;
    private final ObjectMapper objectMapper;

    public CreateLearningPRActivityImpl(DatabaseClient databaseClient, GitHubClient gitHubClient) {
        this.databaseClient = databaseClient;
        this.gitHubClient = gitHubClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void createLearningPR(String repository, int proposedVersion) {
        try {
            // Load proposals and stats
            List<LearnedHeuristic> heuristics = loadProposedHeuristics(repository);
            List<PromptPatch> patches = loadProposedPatches(repository);
            List<SeverityCalibration> calibrations = loadProposedCalibrations(repository);
            Map<String, AgentAccuracy> accuracy = databaseClient.computeAgentAccuracy(repository);
            int totalReviews = databaseClient.countReviewsForRepo(repository);

            if (heuristics.isEmpty() && patches.isEmpty() && calibrations.isEmpty()) {
                return; // Nothing to propose
            }

            // Build report
            String report = buildReport(repository, proposedVersion, totalReviews,
                    accuracy, heuristics, patches, calibrations);

            // TODO: Create GitHub PR via API
            // For now, log the report. Full GitHub PR creation (branch, commit files, open PR)
            // requires multiple API calls that will be implemented when we wire up the full flow.
            System.out.println("=== Learning PR Report (v" + proposedVersion + ") ===");
            System.out.println(report);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create learning PR: " + e.getMessage(), e);
        }
    }

    private String buildReport(String repository, int version, int totalReviews,
                                Map<String, AgentAccuracy> accuracy,
                                List<LearnedHeuristic> heuristics,
                                List<PromptPatch> patches,
                                List<SeverityCalibration> calibrations) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Learning Report v").append(version).append("\n\n");
        sb.append("Repository: ").append(repository).append("\n");
        sb.append("Based on ").append(totalReviews).append(" reviews\n\n");

        // Agent accuracy
        sb.append("## Agent Accuracy\n\n");
        sb.append("| Agent | Findings | Accepted | Dismissed | Precision |\n");
        sb.append("|-------|----------|----------|-----------|----------|\n");
        for (AgentAccuracy a : accuracy.values()) {
            sb.append(String.format("| %s | %d | %d | %d | %.0f%% |\n",
                    a.agentName, a.totalFindings, a.acceptedFindings,
                    a.dismissedFindings, a.precisionRate * 100));
        }

        // Proposed heuristics
        if (!heuristics.isEmpty()) {
            sb.append("\n## Proposed Heuristics\n\n");
            for (LearnedHeuristic h : heuristics) {
                sb.append("- **").append(h.heuristicType).append("**");
                if (h.agentName != null) sb.append(" (").append(h.agentName).append(")");
                sb.append(": ").append(h.description).append("\n");
                sb.append("  Evidence: ").append(h.evidence).append("\n\n");
            }
        }

        // Proposed prompt patches
        if (!patches.isEmpty()) {
            sb.append("\n## Proposed Prompt Patches\n\n");
            for (PromptPatch p : patches) {
                sb.append("- **").append(p.patchType).append("** (").append(p.agentName).append("): ");
                sb.append(p.description).append("\n");
                sb.append("  Content: `").append(p.patchContent).append("`\n");
                sb.append("  Evidence: ").append(p.evidence).append("\n\n");
            }
        }

        // Proposed calibrations
        if (!calibrations.isEmpty()) {
            sb.append("\n## Proposed Severity Calibrations\n\n");
            for (SeverityCalibration c : calibrations) {
                sb.append(String.format("- **%s** (%s): %s → %s (confidence: %.0f%%, samples: %d)\n",
                        c.agentName, c.category, c.originalLevel, c.calibratedLevel,
                        c.confidence * 100, c.sampleSize));
            }
        }

        return sb.toString();
    }

    private List<LearnedHeuristic> loadProposedHeuristics(String repository) throws Exception {
        // Reuse the load method but filter for PROPOSED status
        // For now, use the active loader — we'll add a PROPOSED filter method
        return databaseClient.loadActiveHeuristics(repository);
    }

    private List<PromptPatch> loadProposedPatches(String repository) throws Exception {
        return databaseClient.loadActivePromptPatches(repository);
    }

    private List<SeverityCalibration> loadProposedCalibrations(String repository) throws Exception {
        return databaseClient.loadActiveCalibrations(repository);
    }
}
