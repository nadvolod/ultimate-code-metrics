package com.utm.temporal.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GitHubClient {
    private static final String API_BASE = "https://api.github.com";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String token;

    public GitHubClient() {
        this(System.getenv("GITHUB_TOKEN"));
    }

    public GitHubClient(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN must be set and non-blank");
        }
        this.token = token;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public PullRequestInfo getPullRequest(String owner, String repo, int prNumber) {
        JsonNode node = apiGet("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
        PullRequestInfo info = new PullRequestInfo();
        info.number = prNumber;
        info.state = node.path("state").asText();
        info.merged = node.path("merged").asBoolean(false);
        info.mergedAt = node.path("merged_at").isNull() ? null : node.path("merged_at").asText();
        info.closedAt = node.path("closed_at").isNull() ? null : node.path("closed_at").asText();
        info.title = node.path("title").asText();
        return info;
    }

    public List<CommitInfo> getPullRequestCommits(String owner, String repo, int prNumber) {
        JsonNode nodes = apiGet("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/commits");
        List<CommitInfo> commits = new ArrayList<>();
        for (JsonNode node : nodes) {
            CommitInfo commit = new CommitInfo();
            commit.sha = node.path("sha").asText();
            commit.message = node.path("commit").path("message").asText();
            commit.date = node.path("commit").path("committer").path("date").asText();
            commits.add(commit);
        }
        return commits;
    }

    public String getPullRequestDiff(String owner, String repo, int prNumber) {
        Request request = new Request.Builder()
                .url(API_BASE + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3.diff")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API failed: HTTP " + response.code());
            }
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch PR diff: " + e.getMessage(), e);
        }
    }

    public List<ReviewCommentInfo> getPullRequestReviewComments(String owner, String repo, int prNumber) {
        JsonNode nodes = apiGet("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/comments");
        List<ReviewCommentInfo> comments = new ArrayList<>();
        for (JsonNode node : nodes) {
            ReviewCommentInfo comment = new ReviewCommentInfo();
            comment.id = node.path("id").asLong();
            comment.user = node.path("user").path("login").asText();
            comment.body = node.path("body").asText();
            comment.createdAt = node.path("created_at").asText();
            comments.add(comment);
        }
        return comments;
    }

    public List<ReviewCommentInfo> getIssueComments(String owner, String repo, int prNumber) {
        JsonNode nodes = apiGet("/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments");
        List<ReviewCommentInfo> comments = new ArrayList<>();
        for (JsonNode node : nodes) {
            ReviewCommentInfo comment = new ReviewCommentInfo();
            comment.id = node.path("id").asLong();
            comment.user = node.path("user").path("login").asText();
            comment.body = node.path("body").asText();
            comment.createdAt = node.path("created_at").asText();
            comments.add(comment);
        }
        return comments;
    }

    public List<JsonNode> searchRevertCommits(String owner, String repo, String since) {
        JsonNode nodes = apiGet("/repos/" + owner + "/" + repo + "/commits?since=" + since);
        List<JsonNode> reverts = new ArrayList<>();
        for (JsonNode node : nodes) {
            String message = node.path("commit").path("message").asText().toLowerCase();
            if (message.startsWith("revert ")) {
                reverts.add(node);
            }
        }
        return reverts;
    }

    private JsonNode apiGet(String path) {
        Request request = new Request.Builder()
                .url(API_BASE + path)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API failed: HTTP " + response.code() + " for " + path);
            }
            return objectMapper.readTree(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException("GitHub API call failed: " + e.getMessage(), e);
        }
    }

    // Simple DTOs
    public static class PullRequestInfo {
        public int number;
        public String state;
        public boolean merged;
        public String mergedAt;
        public String closedAt;
        public String title;
    }

    public static class CommitInfo {
        public String sha;
        public String message;
        public String date;
    }

    public static class ReviewCommentInfo {
        public long id;
        public String user;
        public String body;
        public String createdAt;
    }
}
