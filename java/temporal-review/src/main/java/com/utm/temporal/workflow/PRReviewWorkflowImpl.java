package com.utm.temporal.workflow;

import com.utm.temporal.activity.CodeQualityActivity;
import com.utm.temporal.activity.PriorityActivity;
import com.utm.temporal.activity.SecurityQualityActivity;
import com.utm.temporal.activity.TestQualityActivity;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.Metadata;
import com.utm.temporal.model.ReviewRequest;
import com.utm.temporal.model.ReviewResponse;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

public class PRReviewWorkflowImpl implements PRReviewWorkflow {
    private static final Logger logger = Workflow.getLogger(PRReviewWorkflowImpl.class);

    // 1. configure how activities should behave
    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))  // How long can one attempt take?
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5)) // How long before first retry? 2 sec is better for LLM
                    .setBackoffCoefficient(2)   // Multiply wait time by what?
                    .build())
            .build();

    // 2. create activity stubs for each agent
    private final CodeQualityActivity codeQualityActivity = Workflow.newActivityStub(
            CodeQualityActivity.class, ACTIVITY_OPTIONS
    );
    private final TestQualityActivity testQualityActivity = Workflow.newActivityStub(
            TestQualityActivity.class, ACTIVITY_OPTIONS
    );
    private final SecurityQualityActivity securityQualityActivity = Workflow.newActivityStub(
            SecurityQualityActivity.class, ACTIVITY_OPTIONS
    );
    private final PriorityActivity priorityActivity = Workflow.newActivityStub(
            PriorityActivity.class, ACTIVITY_OPTIONS
    );

    @Override
    public ReviewResponse review(ReviewRequest request) {
        // Use this instead of System.currentTimeMillis()
        long startMs = Workflow.currentTimeMillis();

        logger.info("=".repeat(60));
        logger.info("Starting PR Review: " + request.prTitle);
        logger.info("=".repeat(60));

        try {
            // 1. Call Code Quality Agent
            logger.info("[1/4] Calling Code Quality Agent...");
            AgentResult codeQuality = codeQualityActivity.analyze(request);
            logger.info("      → " + codeQuality.recommendation + " (Risk: " + codeQuality.riskLevel + ")");

            // 2. Call Test Quality Agent
            logger.info("[2/4] Calling Test Quality Agent...");
            AgentResult testQuality = testQualityActivity.analyze(request);
            logger.info("      → " + testQuality.recommendation + " (Risk: " + testQuality.riskLevel + ")");

            // 3. Call Security Agent
            logger.info("[3/4] Calling Security Agent...");
            AgentResult security = securityQualityActivity.analyze(request);
            logger.info("      → " + security.recommendation + " (Risk: " + security.riskLevel + ")");

            // 4. Call Priority Agent with results from other agents
            logger.info("[4/4] Calling Priority Agent...");
            AgentResult priority = priorityActivity.prioritizeIssues(
                    request,
                    Arrays.asList(codeQuality, testQuality, security)
            );
            logger.info("      → " + priority.recommendation + " (Risk: " + priority.riskLevel + ")");

            // 5. Aggregate results from all agents
            String overall = aggregate(codeQuality, testQuality, security, priority);

            // 6. Build response
            long tookMs = Workflow.currentTimeMillis() - startMs;

            Metadata metadata = new Metadata(
                    Instant.ofEpochMilli(Workflow.currentTimeMillis()).toString(),
                    tookMs,
                    System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini")
            );

            ReviewResponse response = new ReviewResponse(
                    overall,
                    Arrays.asList(codeQuality, testQuality, security, priority),
                    metadata,
                    request.prNumber,
                    request.prTitle,
                    request.author
            );

            logger.info("=".repeat(60));
            logger.info("Review Complete: " + overall + " (took " + tookMs + "ms)");
            logger.info("=".repeat(60));

            return response;

        } catch (Exception e) {
            // Fail fast - no retry logic, no graceful degradation
            System.err.println("Review failed: " + e.getMessage());
            throw new RuntimeException("Review orchestration failed", e);
        }

    }
    private String aggregate(AgentResult... results) {
        // Check for BLOCK
        for (AgentResult result : results) {
            if ("BLOCK".equals(result.recommendation)) {
                return "BLOCK";
            }
        }

        // Check for REQUEST_CHANGES
        for (AgentResult result : results) {
            if ("REQUEST_CHANGES".equals(result.recommendation)) {
                return "REQUEST_CHANGES";
            }
        }

        // All agents approved
        return "APPROVE";
    }
}
