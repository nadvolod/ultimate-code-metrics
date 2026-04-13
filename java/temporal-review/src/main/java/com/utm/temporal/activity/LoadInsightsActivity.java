package com.utm.temporal.activity;

import com.utm.temporal.model.LearningInsights;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface LoadInsightsActivity {
    @ActivityMethod(name = "LoadLearningInsights")
    LearningInsights loadInsights(String repository);
}
