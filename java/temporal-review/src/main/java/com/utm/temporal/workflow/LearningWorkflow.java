package com.utm.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface LearningWorkflow {
    @WorkflowMethod
    void learn(String repository);
}
