package com.utm.temporal.activity;

import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.model.ReviewOutcome;

public class OutcomeRecordingActivityImpl implements OutcomeRecordingActivity {
    private final DatabaseClient client;

    public OutcomeRecordingActivityImpl(DatabaseClient client) {
        this.client = client;
    }
    @Override
    public void recordReviewOutcome(ReviewOutcome outcome) {
        try {
            int prId = client.upsertPullRequest(
                    outcome.repository, outcome.prNumber,
                    outcome.prTitle, outcome.prDescription, outcome.author);

            int reviewRunId = client.saveReviewRun(
                    prId, outcome.reviewId, outcome.learningVersion,
                    outcome.systemRecommendation, outcome.agentResults,
                    outcome.tookMs, outcome.model);

            client.saveFindings(reviewRunId, outcome.agentResults);
        } catch (Exception e) {
            throw new RuntimeException("Failed to record review outcome: " + e.getMessage(), e);
        }
    }
}
