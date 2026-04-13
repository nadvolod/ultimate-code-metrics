package com.utm.temporal.workflow;

import com.utm.temporal.activity.CodeQualityActivity;
import com.utm.temporal.activity.PriorityActivity;
import com.utm.temporal.activity.ComplexityQualityActivity;
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
import java.util.ArrayList;
import java.util.List;

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
    private final ComplexityQualityActivity complexityQualityActivity = Workflow.newActivityStub(
            ComplexityQualityActivity.class, ACTIVITY_OPTIONS
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
            // Collect all agent results in a list
            List<AgentResult> results = new ArrayList<>();

            // 1. Call Code Quality Agent
            logger.info("[1/5] Calling Code Quality Agent...");
            AgentResult codeQuality = codeQualityActivity.analyze(request);
            results.add(codeQuality);
            logger.info("      → " + codeQuality.recommendation + " (Risk: " + codeQuality.riskLevel + ")");

            // 2. Call Test Quality Agent
            logger.info("[2/5] Calling Test Quality Agent...");
            AgentResult testQuality = testQualityActivity.analyze(request);
            results.add(testQuality);
            logger.info("      → " + testQuality.recommendation + " (Risk: " + testQuality.riskLevel + ")");

            // 3. Call Security Agent
            logger.info("[3/5] Calling Security Agent...");
            AgentResult security = securityQualityActivity.analyze(request);
            results.add(security);
            logger.info("      → " + security.recommendation + " (Risk: " + security.riskLevel + ")");

            // 4. Call Complexity Agent
            logger.info("[4/5] Calling Complexity Agent...");
            AgentResult complexity = complexityQualityActivity.analyze(request);
            results.add(complexity);
            logger.info("      → " + complexity.recommendation + " (Risk: " + complexity.riskLevel + ")");

            // 5. Call Priority Agent with results from other agents
            logger.info("[5/5] Calling Priority Agent...");
            AgentResult priority = priorityActivity.prioritizeIssues(request, results);
            results.add(priority);
            logger.info("      → " + priority.recommendation + " (Risk: " + priority.riskLevel + ")");

            // 6. Aggregate results from all agents
            String overall = aggregate(results);

            // 7. Build response
            long tookMs = Workflow.currentTimeMillis() - startMs;
            String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");

            // Sum token usage across all agents
            int totalPrompt = 0;
            int totalCompletion = 0;
            for (AgentResult r : results) {
                totalPrompt += r.promptTokens;
                totalCompletion += r.completionTokens;
            }

            Metadata metadata = new Metadata(
                    Instant.ofEpochMilli(Workflow.currentTimeMillis()).toString(),
                    tookMs,
                    model
            );
            metadata.totalPromptTokens = totalPrompt;
            metadata.totalCompletionTokens = totalCompletion;
            metadata.estimatedCost = estimateCost(model, totalPrompt, totalCompletion);

            ReviewResponse response = new ReviewResponse(
                    overall,
                    results,
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
    /**
     * Estimate cost in USD based on model and token counts.
     * Pricing per 1M tokens (as of 2025):
     *   gpt-4o-mini:  input $0.15,  output $0.60
     *   gpt-4o:       input $2.50,  output $10.00
     *   gpt-4.1-mini: input $0.40,  output $1.60
     *   gpt-4.1:      input $2.00,  output $8.00
     *   gpt-5.4-mini: input $0.40,  output $1.60
     */
    static double estimateCost(String model, int promptTokens, int completionTokens) {
        double inputPer1M;
        double outputPer1M;
        if (model != null && model.contains("4o-mini")) {
            inputPer1M = 0.15; outputPer1M = 0.60;
        } else if (model != null && model.contains("4o")) {
            inputPer1M = 2.50; outputPer1M = 10.00;
        } else if (model != null && model.contains("4.1-mini")) {
            inputPer1M = 0.40; outputPer1M = 1.60;
        } else if (model != null && model.contains("4.1")) {
            inputPer1M = 2.00; outputPer1M = 8.00;
        } else {
            // Default to gpt-4o-mini pricing for unknown models
            inputPer1M = 0.40; outputPer1M = 1.60;
        }
        return (promptTokens * inputPer1M + completionTokens * outputPer1M) / 1_000_000.0;
    }

    static String aggregate(List<AgentResult> results) {
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
