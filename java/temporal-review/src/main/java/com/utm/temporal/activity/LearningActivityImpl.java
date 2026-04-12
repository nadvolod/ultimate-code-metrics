package com.utm.temporal.activity;

import com.utm.temporal.agent.LearningAgent;
import com.utm.temporal.agent.LearningAgent.LearningProposals;
import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.model.*;

import java.util.List;
import java.util.Map;

public class LearningActivityImpl implements LearningActivity {
    private final LearningAgent agent;
    private final DatabaseClient databaseClient;

    public LearningActivityImpl(LearningAgent agent, DatabaseClient databaseClient) {
        this.agent = agent;
        this.databaseClient = databaseClient;
    }

    @Override
    public void analyzeOutcomes(String repository) {
        try {
            // 1. Compute stats from DB (deterministic)
            Map<String, AgentAccuracy> accuracyStats = databaseClient.computeAgentAccuracy(repository);
            List<FindingOutcome> allFindings = databaseClient.loadAllFindingsWithOutcomes(repository);
            int totalReviews = databaseClient.countReviewsForRepo(repository);

            if (totalReviews < 5) {
                return; // Not enough data to learn from
            }

            int currentVersion = databaseClient.getCurrentLearningVersion(repository);

            // 2. Use LLM for pattern analysis (semantic)
            LearningProposals proposals = agent.analyze(repository, accuracyStats, allFindings, totalReviews);

            // 3. Save proposals to DB (all as PROPOSED)
            for (LearnedHeuristic h : proposals.heuristics) {
                h.learningVersion = currentVersion;
                databaseClient.saveHeuristic(h);
            }
            for (PromptPatch p : proposals.promptPatches) {
                p.learningVersion = currentVersion;
                databaseClient.savePromptPatch(p);
            }
            for (SeverityCalibration c : proposals.calibrations) {
                c.learningVersion = currentVersion;
                databaseClient.saveSeverityCalibration(c);
            }

            // 4. Save agent precision profiles
            for (Map.Entry<String, AgentAccuracy> entry : accuracyStats.entrySet()) {
                databaseClient.saveAgentPrecisionProfile(
                        repository, entry.getKey(), currentVersion, entry.getValue());
            }

        } catch (Exception e) {
            throw new RuntimeException("Learning analysis failed: " + e.getMessage(), e);
        }
    }
}
