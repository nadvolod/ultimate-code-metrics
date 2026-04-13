package com.utm.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface FindingDispositionActivity {
    @ActivityMethod(name = "InferFindingDispositions")
    void inferDispositions(String repository);
}

