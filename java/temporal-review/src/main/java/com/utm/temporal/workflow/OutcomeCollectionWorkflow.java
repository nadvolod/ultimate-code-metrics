package com.utm.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OutcomeCollectionWorkflow {
    @WorkflowMethod
    void collect(String repository);
}
