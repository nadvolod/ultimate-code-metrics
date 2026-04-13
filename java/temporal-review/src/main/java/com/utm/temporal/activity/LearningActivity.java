package com.utm.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface LearningActivity {
    @ActivityMethod(name = "AnalyzeOutcomes")
    int analyzeOutcomes(String repository);
}
