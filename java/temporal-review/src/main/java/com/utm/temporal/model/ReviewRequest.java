package com.utm.temporal.model;

/**
 * Request body for POST /review endpoint.
 */
public class ReviewRequest {
    public Integer prNumber;  // PR number for tracking
    public String prTitle;
    public String prDescription;
    public String author;     // PR author
    public String diff;
    public TestSummary testSummary;  // Optional

    // No-arg constructor required for Jackson deserialization
    public ReviewRequest() {}

    public ReviewRequest(Integer prNumber, String prTitle, String prDescription, String author, String diff, TestSummary testSummary) {
        this.prNumber = prNumber;
        this.prTitle = prTitle;
        this.prDescription = prDescription;
        this.author = author;
        this.diff = diff;
        this.testSummary = testSummary;
    }
}
