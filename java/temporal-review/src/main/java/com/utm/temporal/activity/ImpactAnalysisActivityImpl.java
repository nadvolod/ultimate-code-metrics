package com.utm.temporal.activity;

import com.utm.temporal.agent.ImpactAnalysisAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class ImpactAnalysisActivityImpl implements ImpactAnalysisActivity {
    private final ImpactAnalysisAgent impactAnalysisAgent;

    public ImpactAnalysisActivityImpl(ImpactAnalysisAgent impactAnalysisAgent) {
        this.impactAnalysisAgent = impactAnalysisAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return impactAnalysisAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff);
    }
}
