package com.utm.temporal.model;

/**
 * Metadata about the review execution.
 */
public class Metadata {
    public String generatedAt;      // ISO-8601 timestamp
    public long tookMs;             // Execution duration in milliseconds
    public String model;            // LLM model used (e.g., "gpt-4")
    public int totalPromptTokens;   // Sum of prompt tokens across all agents
    public int totalCompletionTokens; // Sum of completion tokens across all agents
    public double estimatedCost;    // Estimated cost in USD

    // No-arg constructor required for Jackson deserialization
    public Metadata() {}

    public Metadata(String generatedAt, long tookMs, String model) {
        this.generatedAt = generatedAt;
        this.tookMs = tookMs;
        this.model = model;
    }
}
