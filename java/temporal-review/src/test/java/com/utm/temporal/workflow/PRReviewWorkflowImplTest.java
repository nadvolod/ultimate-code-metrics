package com.utm.temporal.workflow;

import com.utm.temporal.config.AppConfig;
import com.utm.temporal.model.AgentResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PRReviewWorkflowImplTest {

    @BeforeAll
    static void setUp() {
        // PRReviewWorkflowImpl's static initializer calls AppConfig.getActivityTimeoutSeconds(),
        // which requires validate() to have run first. DUMMY_MODE=true (set via surefire env)
        // allows validate() to succeed without a real OPENAI_API_KEY.
        AppConfig.validate();
    }

    // -------------------------------------------------------------------------
    // estimateCost – pricing per known model
    // -------------------------------------------------------------------------

    @Test
    void estimateCost_gpt4oMini_usesCorrectPricing() {
        // gpt-4o-mini: input $0.15, output $0.60 per 1M tokens
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4o-mini", 1_000_000, 1_000_000);
        assertEquals(0.75, cost, 1e-9);
    }

    @Test
    void estimateCost_gpt4o_usesCorrectPricing() {
        // gpt-4o: input $2.50, output $10.00 per 1M tokens
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4o", 1_000_000, 1_000_000);
        assertEquals(12.50, cost, 1e-9);
    }

    @Test
    void estimateCost_gpt41Mini_usesCorrectPricing() {
        // gpt-4.1-mini: input $0.40, output $1.60 per 1M tokens
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4.1-mini", 1_000_000, 1_000_000);
        assertEquals(2.00, cost, 1e-9);
    }

    @Test
    void estimateCost_gpt41_usesCorrectPricing() {
        // gpt-4.1: input $2.00, output $8.00 per 1M tokens
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4.1", 1_000_000, 1_000_000);
        assertEquals(10.00, cost, 1e-9);
    }

    @Test
    void estimateCost_gpt54Mini_usesCorrectPricing() {
        // gpt-5.4-mini: input $0.40, output $1.60 per 1M tokens
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-5.4-mini", 1_000_000, 1_000_000);
        assertEquals(2.00, cost, 1e-9);
    }

    // -------------------------------------------------------------------------
    // estimateCost – model matching specificity (4.1-mini must not match 4.1)
    // -------------------------------------------------------------------------

    @Test
    void estimateCost_gpt41Mini_doesNotMatchGpt41Pricing() {
        double miniCost = PRReviewWorkflowImpl.estimateCost("gpt-4.1-mini", 1_000_000, 1_000_000);
        double fullCost = PRReviewWorkflowImpl.estimateCost("gpt-4.1", 1_000_000, 1_000_000);
        assertNotEquals(fullCost, miniCost, "gpt-4.1-mini should not use gpt-4.1 pricing");
    }

    @Test
    void estimateCost_gpt4oMini_doesNotMatchGpt4oPricing() {
        double miniCost = PRReviewWorkflowImpl.estimateCost("gpt-4o-mini", 1_000_000, 1_000_000);
        double fullCost = PRReviewWorkflowImpl.estimateCost("gpt-4o", 1_000_000, 1_000_000);
        assertNotEquals(fullCost, miniCost, "gpt-4o-mini should not use gpt-4o pricing");
    }

    // -------------------------------------------------------------------------
    // estimateCost – null / unknown model fallback
    // -------------------------------------------------------------------------

    @Test
    void estimateCost_nullModel_defaultsToGpt4oMiniPricing() {
        double cost = PRReviewWorkflowImpl.estimateCost(null, 1_000_000, 1_000_000);
        assertEquals(0.75, cost, 1e-9);
    }

    @Test
    void estimateCost_unknownModel_defaultsToGpt4oMiniPricing() {
        double cost = PRReviewWorkflowImpl.estimateCost("some-future-model", 1_000_000, 1_000_000);
        assertEquals(0.75, cost, 1e-9);
    }

    // -------------------------------------------------------------------------
    // estimateCost – token edge cases
    // -------------------------------------------------------------------------

    @Test
    void estimateCost_zeroTokens_returnsZero() {
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4o-mini", 0, 0);
        assertEquals(0.0, cost, 1e-9);
    }

    @Test
    void estimateCost_promptTokensOnly_excludesOutputCost() {
        // Only prompt tokens; completion = 0
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4o-mini", 1_000_000, 0);
        assertEquals(0.15, cost, 1e-9);
    }

    @Test
    void estimateCost_completionTokensOnly_excludesInputCost() {
        // Only completion tokens; prompt = 0
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4o-mini", 0, 1_000_000);
        assertEquals(0.60, cost, 1e-9);
    }

    @Test
    void estimateCost_smallTokenCount_returnsSmallPositiveValue() {
        // Typical small PR: ~1000 prompt + 500 completion tokens with gpt-4o-mini
        double cost = PRReviewWorkflowImpl.estimateCost("gpt-4o-mini", 1_000, 500);
        double expected = (1_000 * 0.15 + 500 * 0.60) / 1_000_000.0;
        assertEquals(expected, cost, 1e-12);
        assertTrue(cost > 0.0);
    }

    // -------------------------------------------------------------------------
    // aggregate – recommendation precedence
    // -------------------------------------------------------------------------

    @Test
    void aggregate_allApprove_returnsApprove() {
        List<AgentResult> results = Arrays.asList(
                resultWith("APPROVE"),
                resultWith("APPROVE"),
                resultWith("APPROVE")
        );
        assertEquals("APPROVE", PRReviewWorkflowImpl.aggregate(results));
    }

    @Test
    void aggregate_oneRequestChanges_returnsRequestChanges() {
        List<AgentResult> results = Arrays.asList(
                resultWith("APPROVE"),
                resultWith("REQUEST_CHANGES"),
                resultWith("APPROVE")
        );
        assertEquals("REQUEST_CHANGES", PRReviewWorkflowImpl.aggregate(results));
    }

    @Test
    void aggregate_oneBlock_returnsBlock() {
        List<AgentResult> results = Arrays.asList(
                resultWith("APPROVE"),
                resultWith("BLOCK"),
                resultWith("APPROVE")
        );
        assertEquals("BLOCK", PRReviewWorkflowImpl.aggregate(results));
    }

    @Test
    void aggregate_blockTakesPriorityOverRequestChanges() {
        List<AgentResult> results = Arrays.asList(
                resultWith("REQUEST_CHANGES"),
                resultWith("BLOCK"),
                resultWith("APPROVE")
        );
        assertEquals("BLOCK", PRReviewWorkflowImpl.aggregate(results));
    }

    @Test
    void aggregate_emptyList_returnsApprove() {
        assertEquals("APPROVE", PRReviewWorkflowImpl.aggregate(Collections.emptyList()));
    }

    @Test
    void aggregate_singleApprove_returnsApprove() {
        assertEquals("APPROVE", PRReviewWorkflowImpl.aggregate(
                Collections.singletonList(resultWith("APPROVE"))));
    }

    @Test
    void aggregate_singleBlock_returnsBlock() {
        assertEquals("BLOCK", PRReviewWorkflowImpl.aggregate(
                Collections.singletonList(resultWith("BLOCK"))));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AgentResult resultWith(String recommendation) {
        AgentResult r = new AgentResult();
        r.recommendation = recommendation;
        return r;
    }
}
