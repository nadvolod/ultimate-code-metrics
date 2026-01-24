package com.utm.temporal.activity;

import com.utm.temporal.agent.DuplicationAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class DuplicationQualityActivityImpl implements DuplicationQualityActivity {
    private final DuplicationAgent duplicationAgent;

    public DuplicationQualityActivityImpl(DuplicationAgent duplicationAgent) {
        this.duplicationAgent = duplicationAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return duplicationAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff);
    }
}
