package com.utm.temporal.activity;

import com.utm.temporal.agent.TestQualityAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class TestQualityActivityImpl implements TestQualityActivity {
    private final TestQualityAgent testQualityAgent;

    public TestQualityActivityImpl(TestQualityAgent testQualityAgent) {
        this.testQualityAgent = testQualityAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return testQualityAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff,
                pullRequest.testSummary);
    }
}
