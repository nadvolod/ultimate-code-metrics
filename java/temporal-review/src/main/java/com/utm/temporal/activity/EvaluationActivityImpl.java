package com.utm.temporal.activity;

import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.model.AgentAccuracy;

import java.util.Map;

public class EvaluationActivityImpl implements EvaluationActivity {
    private final DatabaseClient databaseClient;

    public EvaluationActivityImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public void computeSnapshot(String repository) {
        try {
            int version = databaseClient.getCurrentLearningVersion(repository);
            int totalReviews = databaseClient.countReviewsForRepo(repository);
            int totalFindings = databaseClient.countFindingsForRepo(repository);
            int mergedPRs = databaseClient.countMergedPRs(repository);
            int buggyPRs = databaseClient.countMergedPRsWithBugs(repository);

            Map<String, AgentAccuracy> accuracy = databaseClient.computeAgentAccuracy(repository);

            // Aggregate across all agents
            int totalAccepted = 0, totalDismissed = 0, totalWithOutcomes = 0;
            for (AgentAccuracy a : accuracy.values()) {
                totalAccepted += a.acceptedFindings;
                totalDismissed += a.dismissedFindings;
                totalWithOutcomes += a.totalFindings;
            }

            double acceptedRate = totalWithOutcomes > 0 ? (double) totalAccepted / totalWithOutcomes : 0;
            double falsePositiveRate = totalWithOutcomes > 0 ? (double) totalDismissed / totalWithOutcomes : 0;
            double postMergeBugRate = mergedPRs > 0 ? (double) buggyPRs / mergedPRs : 0;
            double avgFindings = totalReviews > 0 ? (double) totalFindings / totalReviews : 0;
            double highSignalFindings = totalReviews > 0 ? (double) totalAccepted / totalReviews : 0;

            databaseClient.saveEvaluationSnapshot(
                    repository, version,
                    acceptedRate, falsePositiveRate,
                    0.0,  // missed_issue_rate — requires deeper analysis, placeholder for now
                    postMergeBugRate,
                    avgFindings, highSignalFindings,
                    0.0,  // reviewer_agreement_rate — placeholder
                    0.0,  // avg_time_to_merge — placeholder
                    totalReviews, totalFindings);

        } catch (Exception e) {
            throw new RuntimeException("Evaluation snapshot failed: " + e.getMessage(), e);
        }
    }
}
