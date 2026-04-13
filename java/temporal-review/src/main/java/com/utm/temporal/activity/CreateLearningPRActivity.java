package com.utm.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CreateLearningPRActivity {
    @ActivityMethod(name = "CreateLearningPR")
    void createLearningPR(String repository, int proposedVersion);
}
