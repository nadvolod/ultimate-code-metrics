package com.utm.temporal.activity;

import com.utm.temporal.agent.FindingDispositionAgent;
import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.github.GitHubClient;
import com.utm.temporal.model.FindingOutcome;
import com.utm.temporal.model.ReviewOutcome;

import java.util.List;

public class FindingDispositionActivityImpl implements FindingDispositionActivity {
    private final FindingDispositionAgent agent;
    private final GitHubClient gitHubClient;
    private final DatabaseClient databaseClient;

    public FindingDispositionActivityImpl(FindingDispositionAgent agent,
                                          GitHubClient gitHubClient,
                                          DatabaseClient databaseClient) {
        this.agent = agent;
        this.gitHubClient = gitHubClient;
        this.databaseClient = databaseClient;
    }

    @Override
    public void inferDispositions(String repository) {
        try {
            String[] parts = repository.split("/");
            String owner = parts[0];
            String repo = parts[1];

            List<ReviewOutcome> pending = databaseClient.loadReviewsWithPendingFindings(repository);

            for (ReviewOutcome outcome : pending) {
                // Load findings that don't have outcomes yet
                List<FindingOutcome> findings = databaseClient.loadFindingsForReview(outcome.reviewId);
                if (findings.isEmpty()) continue;

                // Get the follow-up diff
                String diff = gitHubClient.getPullRequestDiff(owner, repo, outcome.prNumber);

                // Use LLM to infer dispositions
                List<FindingOutcome> results = agent.inferDispositions(findings, diff);

                // Save each disposition
                for (FindingOutcome result : results) {
                    databaseClient.saveFindingOutcome(result.findingId, result.disposition, result.evidence);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Finding disposition inference failed: " + e.getMessage(), e);
        }
    }
}
