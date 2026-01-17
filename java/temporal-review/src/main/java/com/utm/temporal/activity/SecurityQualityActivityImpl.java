package com.utm.temporal.activity;

import com.utm.temporal.agent.SecurityAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class SecurityQualityActivityImpl implements SecurityQualityActivity {
    private final SecurityAgent securityAgent;

    public SecurityQualityActivityImpl(SecurityAgent securityAgent) {
        this.securityAgent = securityAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return securityAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff);
    }
}
