package com.utm.temporal.activity;

import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CodeQualityActivity {
    @ActivityMethod(name = "AnalyzeCodeQuality")
    AgentResult analyze(ReviewRequest pullRequest);
}
