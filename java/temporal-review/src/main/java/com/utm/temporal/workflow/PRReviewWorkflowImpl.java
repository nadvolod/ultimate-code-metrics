package com.utm.temporal.workflow;

import com.utm.temporal.activity.*;
import com.utm.temporal.config.AppConfig;
import com.utm.temporal.learning.HeuristicsEngine;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.*;
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

    // 1. configure how activities should behave.
    //    AppConfig getters return values cached at startup (via validate()),
    //    so this static initializer is replay-safe — it will never read
    //    live environment variables during workflow execution.
    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(AppConfig.getActivityTimeoutSeconds()))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(AppConfig.getRetryIntervalSeconds()))
                    .setBackoffCoefficient(2)
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
    private final ImpactAnalysisActivity impactAnalysisActivity = Workflow.newActivityStub(
            ImpactAnalysisActivity.class, ACTIVITY_OPTIONS
    );
    private final PriorityActivity priorityActivity = Workflow.newActivityStub(
            PriorityActivity.class, ACTIVITY_OPTIONS
    );
    private final OutcomeRecordingActivity outcomeRecordingActivity = Workflow.newActivityStub(
            OutcomeRecordingActivity.class, ACTIVITY_OPTIONS
    );
    private final LoadInsightsActivity loadInsightsActivity = Workflow.newActivityStub(
            LoadInsightsActivity.class, ACTIVITY_OPTIONS
    );

    @Override
    public ReviewResponse review(ReviewRequest request) {
        // Use this instead of System.currentTimeMillis()
        long startMs = Workflow.currentTimeMillis();

        logger.info("=".repeat(60));
        logger.info("Starting PR Review: " + request.prTitle);
        logger.info("=".repeat(60));

        try {
            // Step 0: Load learning insights (null if none exist yet)
            LearningInsights insights = null;
            if (request.repository != null) {
                try {
                    insights = loadInsightsActivity.loadInsights(request.repository);
                    if (insights != null) {
                        logger.info("Loaded learning insights v" + insights.learningVersion);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load insights, proceeding without: " + e.getMessage());
                }
            }

            // TODO: Wire prompt patches into activity calls once activities accept context.
            // Use HeuristicsEngine.buildPromptContext(patches, agentName) per agent.

            HeuristicsEngine heuristicsEngine = new HeuristicsEngine(insights);

            // Collect all agent results in a list
            List<AgentResult> results = new ArrayList<>();

            // 1. Call Code Quality Agent
            logger.info("[1/6] Calling Code Quality Agent...");
            AgentResult codeQuality = codeQualityActivity.analyze(request);
            codeQuality = heuristicsEngine.apply(codeQuality, request.diff);
            results.add(codeQuality);
            logger.info("      → " + codeQuality.recommendation + " (Risk: " + codeQuality.riskLevel + ")");

            // 2. Call Test Quality Agent
            logger.info("[2/6] Calling Test Quality Agent...");
            AgentResult testQuality = testQualityActivity.analyze(request);
            testQuality = heuristicsEngine.apply(testQuality, request.diff);
            results.add(testQuality);
            logger.info("      → " + testQuality.recommendation + " (Risk: " + testQuality.riskLevel + ")");

            // 3. Call Security Agent
            logger.info("[3/6] Calling Security Agent...");
            AgentResult security = securityQualityActivity.analyze(request);
            security = heuristicsEngine.apply(security, request.diff);
            results.add(security);
            logger.info("      → " + security.recommendation + " (Risk: " + security.riskLevel + ")");

            // 4. Call Complexity Agent
            logger.info("[4/6] Calling Complexity Agent...");
            AgentResult complexity = complexityQualityActivity.analyze(request);
            complexity = heuristicsEngine.apply(complexity, request.diff);
            results.add(complexity);
            logger.info("      → " + complexity.recommendation + " (Risk: " + complexity.riskLevel + ")");

            // 5. Call Impact Analysis Agent
            logger.info("[5/6] Calling Impact Analysis Agent...");
            AgentResult impactAnalysis = impactAnalysisActivity.analyze(request);
            impactAnalysis = heuristicsEngine.apply(impactAnalysis, request.diff);
            results.add(impactAnalysis);
            logger.info("      → " + impactAnalysis.recommendation + " (Risk: " + impactAnalysis.riskLevel + ")");

            // 6. Call Priority Agent with results from other agents
            logger.info("[6/6] Calling Priority Agent...");
            AgentResult priority = priorityActivity.prioritizeIssues(request, results);
            results.add(priority);
            logger.info("      → " + priority.recommendation + " (Risk: " + priority.riskLevel + ")");

            // 6. Aggregate results from all agents
            String overall = aggregate(results);

            // 7. Build response
            long tookMs = Workflow.currentTimeMillis() - startMs;
            // Workflow.sideEffect makes env reads replay-safe
            String model = Workflow.sideEffect(
                    String.class,
                    () -> System.getenv().getOrDefault("OPENAI_MODEL", OpenAiLlmClient.DEFAULT_MODEL)
            );

            // Sum token usage across all agents
            int totalPrompt = 0;
            int totalCompletion = 0;
            for (AgentResult r : results) {
                totalPrompt += r.promptTokens;
                totalCompletion += r.completionTokens;
            }

            // Record outcome to DB (failure here never breaks the review)
            if (request.repository != null) {
                try {
                    ReviewOutcome outcome = new ReviewOutcome();
                    outcome.reviewId = Workflow.getInfo().getWorkflowId();
                    outcome.repository = request.repository;
                    outcome.prNumber = request.prNumber;
                    outcome.prTitle = request.prTitle;
                    outcome.prDescription = request.prDescription;
                    outcome.author = request.author;
                    outcome.systemRecommendation = overall;
                    outcome.agentResults = results;
                    outcome.tookMs = tookMs;
                    outcome.model = AppConfig.getOpenAiModel();
                    outcome.learningVersion = insights != null ? insights.learningVersion : 0;
                    outcomeRecordingActivity.recordReviewOutcome(outcome);
                } catch (Exception e) {
                    logger.warn("Failed to record review outcome: " + e.getMessage());
                }
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
     *   gpt-5.4-mini: input $0.75,  output $4.50
     */
    static double estimateCost(String model, int promptTokens, int completionTokens) {
        double inputPer1M;
        double outputPer1M;
        if (model != null && model.contains("4o-mini")) {
            inputPer1M = 0.15; outputPer1M = 0.60;
        } else if (model != null && model.contains("4o")) {
            inputPer1M = 2.50; outputPer1M = 10.00;
        } else if (model != null && model.contains("5.4-mini")) {
            inputPer1M = 0.75; outputPer1M = 4.50;
        } else if (model != null && model.contains("4.1-mini")) {
            inputPer1M = 0.40; outputPer1M = 1.60;
        } else if (model != null && model.contains("4.1")) {
            inputPer1M = 2.00; outputPer1M = 8.00;
        } else {
            // Default to gpt-5.4-mini pricing (matches AppConfig.DEFAULT_OPENAI_MODEL)
            inputPer1M = 0.75; outputPer1M = 4.50;
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
