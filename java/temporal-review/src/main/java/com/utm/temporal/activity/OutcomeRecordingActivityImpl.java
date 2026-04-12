package com.utm.temporal.activity;

import com.utm.temporal.model.ReviewOutcome;

public class OutcomeRecordingActivityImpl implements OutcomeRecordingActivity{
    private final DatabaseClient client;

    OutcomeRecordingActivityImpl(DatabaseClient client){
        this.client = client;
    }
    @Override
    public void recordReviewOutcome(ReviewOutcome outcome) {

    }
}
