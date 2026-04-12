package com.utm.temporal.workflow;

import com.utm.temporal.activity.FindingDispositionActivity;
import com.utm.temporal.activity.GitHubOutcomeActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class OutcomeCollectionWorkflowImpl implements OutcomeCollectionWorkflow{
    private static final Logger logger = Workflow.getLogger(OutcomeCollectionWorkflowImpl.class);

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(120))  // How long can one attempt take?
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5)) // How long before first retry? 2 sec is better for LLM
                    .setBackoffCoefficient(2)   // Multiply wait time by what?
                    .build())
            .build();

    // 2. create activity stubs for each agent
    private final FindingDispositionActivity findingDispositionActivity = Workflow.newActivityStub(
            FindingDispositionActivity.class, ACTIVITY_OPTIONS
    );
    // 2. create activity stubs for each agent
    private final GitHubOutcomeActivity gitHubOutcomeActivity = Workflow.newActivityStub(
            GitHubOutcomeActivity.class, ACTIVITY_OPTIONS
    );
    @Override
    public void collect(String repository) {
        logger.info("Collecting outcomes for " + repository);
        gitHubOutcomeActivity.collectOutcomes(repository);
        findingDispositionActivity.inferDispositions(repository);
        logger.info("Outcome collection complete for " + repository);
    }
}
