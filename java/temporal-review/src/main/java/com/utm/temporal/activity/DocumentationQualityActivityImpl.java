package com.utm.temporal.activity;

import com.utm.temporal.agent.DocumentationAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

public class DocumentationQualityActivityImpl implements DocumentationQualityActivity {
    private final DocumentationAgent documentationAgent;

    public DocumentationQualityActivityImpl(DocumentationAgent documentationAgent) {
        this.documentationAgent = documentationAgent;
    }

    @Override
    public AgentResult analyze(ReviewRequest pullRequest) {
        return documentationAgent.analyze(pullRequest.prTitle,
                pullRequest.prDescription, pullRequest.diff);
    }
}
