package com.utm.temporal.workflow;

import com.utm.temporal.model.ReviewRequest;
import com.utm.temporal.model.ReviewResponse;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PRReviewWorkflow {
    @WorkflowMethod
    ReviewResponse review(ReviewRequest request);
}
