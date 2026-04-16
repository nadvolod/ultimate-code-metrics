package com.utm.temporal.activity;

import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ImpactAnalysisActivity {
    @ActivityMethod(name = "AnalyzeImpact")
    AgentResult analyze(ReviewRequest pullRequest);
}
