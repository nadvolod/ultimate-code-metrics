package com.utm.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface EvaluationActivity {
    @ActivityMethod(name = "ComputeEvaluationSnapshot")
    void computeSnapshot(String repository);
}
