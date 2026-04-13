package com.utm.temporal.activity;

import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.github.GitHubClient;
import com.utm.temporal.github.GitHubClient.*;
import com.utm.temporal.model.ReviewOutcome;

import java.time.Instant;
import java.util.List;

public class GitHubOutcomeActivityImpl implements GitHubOutcomeActivity {
    private final GitHubClient gitHubClient;
    private final DatabaseClient databaseClient;

    public GitHubOutcomeActivityImpl(GitHubClient gitHubClient, DatabaseClient databaseClient) {
        this.gitHubClient = gitHubClient;
        this.databaseClient = databaseClient;
    }

    @Override
    public void collectOutcomes(String repository) {
        try {
            String[] parts = repository.split("/");
            String owner = parts[0];
            String repo = parts[1];

            List<ReviewOutcome> pending = databaseClient.loadPendingOutcomes(repository);

            for (ReviewOutcome outcome : pending) {
                PullRequestInfo prInfo = gitHubClient.getPullRequest(owner, repo, outcome.prNumber);

                // Determine status
                String status;
                if (prInfo.merged) {
                    status = "MERGED";
                } else if ("closed".equals(prInfo.state)) {
                    status = "CLOSED";
                } else {
                    continue; // Still open, skip
                }

                // Update PR status in DB
                databaseClient.updatePullRequestStatus(
                        repository, outcome.prNumber, status, prInfo.mergedAt, prInfo.closedAt);

                if (prInfo.merged) {
                    // Check for follow-up commits after the review
                    List<CommitInfo> commits = gitHubClient.getPullRequestCommits(owner, repo, outcome.prNumber);
                    boolean hadFollowUp = commits.stream()
                            .anyMatch(c -> c.date.compareTo(outcome.reviewedAt) > 0);

                    // Check for reverts
                    boolean hadRevert = false;
                    Integer revertPrNumber = null;
                    var reverts = gitHubClient.searchRevertCommits(owner, repo, prInfo.mergedAt);
                    for (var revert : reverts) {
                        String msg = revert.path("commit").path("message").asText();
                        if (msg.contains("#" + outcome.prNumber) || msg.contains(outcome.prTitle)) {
                            hadRevert = true;
                            break;
                        }
                    }

                    // Save post-merge outcome
                    int prId = databaseClient.getPullRequestId(repository, outcome.prNumber);
                    if (prId > 0) {
                        databaseClient.savePostMergeOutcome(prId, hadRevert, revertPrNumber, hadFollowUp);
                    }

                    // TODO: Re-enable once saveReviewComments persists to DB instead of just fetching
                    // saveReviewComments(owner, repo, outcome.prNumber, prId);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Outcome collection failed: " + e.getMessage(), e);
        }
    }

    private void saveReviewComments(String owner, String repo, int prNumber, int prId) throws Exception {
        List<ReviewCommentInfo> prComments = gitHubClient.getPullRequestReviewComments(owner, repo, prNumber);
        List<ReviewCommentInfo> issueComments = gitHubClient.getIssueComments(owner, repo, prNumber);

        // TODO: Save to review_comments table via DatabaseClient
        // For now, comments are fetched but not persisted until we add the DB method
    }
}
