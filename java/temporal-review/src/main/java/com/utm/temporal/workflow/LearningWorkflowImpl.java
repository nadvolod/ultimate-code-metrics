package com.utm.temporal.workflow;

import com.utm.temporal.activity.CreateLearningPRActivity;
import com.utm.temporal.activity.LearningActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class LearningWorkflowImpl implements LearningWorkflow {
    private static final Logger logger = Workflow.getLogger(LearningWorkflowImpl.class);

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(120))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5))
                    .setBackoffCoefficient(2)
                    .build())
            .build();

    private final LearningActivity learningActivity = Workflow.newActivityStub(
            LearningActivity.class, ACTIVITY_OPTIONS
    );
    private final CreateLearningPRActivity createLearningPRActivity = Workflow.newActivityStub(
            CreateLearningPRActivity.class, ACTIVITY_OPTIONS
    );

    @Override
    public void learn(String repository) {
        logger.info("Starting learning analysis for " + repository);
        learningActivity.analyzeOutcomes(repository);
        logger.info("Learning analysis complete, creating PR for review");
        createLearningPRActivity.createLearningPR(repository, 0);
        logger.info("Learning PR created for " + repository);
    }
}
