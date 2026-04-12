package com.utm.temporal.model;

import java.util.List;

public class ReviewOutcome {
    public String reviewId;
    public String repository;
    public Integer prNumber;
    public String prTitle;
    public String prDescription;
    public String author;
    public String reviewedAt;
    public String systemRecommendation;
    public List<AgentResult> agentResults;
    public long tookMs;
    public String model;
    public int learningVersion;

    // Filled in later by OutcomeCollectionSchedule
    public String maintainerDecision;
    public List<FindingOutcome> findingOutcomes;
    public Boolean hadFollowUpFixes;
    public Boolean hadRevert;

    public ReviewOutcome() {}
}
