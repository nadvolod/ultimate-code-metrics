package com.utm.temporal.model;

import java.util.List;
import java.util.Map;

public class LearningInsights {
    public String repository;
    public int learningVersion;
    public List<LearnedHeuristic> activeHeuristics;
    public List<PromptPatch> activePromptPatches;
    public List<SeverityCalibration> activeCalibrations;
    public Map<String, AgentAccuracy> agentAccuracyStats;

    public LearningInsights() {}
}
