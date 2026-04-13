package com.utm.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface GitHubOutcomeActivity {
    @ActivityMethod(name = "CollectGitHubOutcomes")
    void collectOutcomes(String repository);
}
