package com.utm.temporal.activity;

import com.utm.temporal.model.ReviewOutcome;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface OutcomeRecordingActivity {
    @ActivityMethod(name = "RecordReviewOutcome")
    void recordReviewOutcome(ReviewOutcome outcome);
}
