package com.utm.temporal.activity;

import com.utm.temporal.agent.CodeQualityAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class CodeQualityActivityImpl implements CodeQualityActivity {
    private final CodeQualityAgent codeQualityAgent;

    public CodeQualityActivityImpl(CodeQualityAgent codeQualityAgent) {
        this.codeQualityAgent = codeQualityAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return codeQualityAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff);
    }
}
