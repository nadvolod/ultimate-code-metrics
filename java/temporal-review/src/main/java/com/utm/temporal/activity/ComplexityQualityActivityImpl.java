package com.utm.temporal.activity;

import com.utm.temporal.agent.ComplexityAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class ComplexityQualityActivityImpl implements ComplexityQualityActivity {
    private final ComplexityAgent complexityAgent;

    public ComplexityQualityActivityImpl(ComplexityAgent complexityAgent) {
        this.complexityAgent = complexityAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return complexityAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff);
    }
}
