package com.utm.temporal.workflow;

import com.utm.temporal.activity.EvaluationActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class EvaluationWorkflowImpl implements EvaluationWorkflow {
    private static final Logger logger = Workflow.getLogger(EvaluationWorkflowImpl.class);

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5))
                    .setBackoffCoefficient(2)
                    .build())
            .build();

    private final EvaluationActivity evaluationActivity = Workflow.newActivityStub(
            EvaluationActivity.class, ACTIVITY_OPTIONS
    );

    @Override
    public void evaluate(String repository) {
        logger.info("Computing evaluation snapshot for " + repository);
        evaluationActivity.computeSnapshot(repository);
        logger.info("Evaluation snapshot complete for " + repository);
    }
}
