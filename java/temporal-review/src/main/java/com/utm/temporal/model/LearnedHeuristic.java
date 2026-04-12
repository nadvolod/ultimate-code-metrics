package com.utm.temporal.model;

public class LearnedHeuristic {
    public int id;
    public String repository;
    public String agentName;
    public String heuristicType;  // PATH_OVERRIDE, SKIP_CATEGORY, SEVERITY_ADJUST, CUSTOM
    public String rule;           // JSON string of the structured rule
    public String description;
    public String evidence;
    public String status;         // PROPOSED, APPROVED, REJECTED, RETIRED
    public int learningVersion;
    public Integer activatedVersion;

    public LearnedHeuristic() {}
}
