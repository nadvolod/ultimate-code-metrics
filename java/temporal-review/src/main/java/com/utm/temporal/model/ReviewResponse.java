package com.utm.temporal.model;

import java.util.List;

/**
 * Response from the PR review API.
 */
public class ReviewResponse {
    public String overallRecommendation;  // "APPROVE", "REQUEST_CHANGES", "BLOCK"
    public List<AgentResult> agents;
    public Metadata metadata;

    // PR metadata (echoed from request)
    public Integer prNumber;
    public String prTitle;
    public String author;

    // No-arg constructor required for Jackson deserialization
    public ReviewResponse() {}

    public ReviewResponse(String overallRecommendation, List<AgentResult> agents, Metadata metadata) {
        this.overallRecommendation = overallRecommendation;
        this.agents = agents;
        this.metadata = metadata;
    }

    public ReviewResponse(String overallRecommendation, List<AgentResult> agents, Metadata metadata,
                          Integer prNumber, String prTitle, String author) {
        this.overallRecommendation = overallRecommendation;
        this.agents = agents;
        this.metadata = metadata;
        this.prNumber = prNumber;
        this.prTitle = prTitle;
        this.author = author;
    }
}
