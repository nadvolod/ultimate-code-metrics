package com.utm.temporal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.llm.LlmClient;
import com.utm.temporal.llm.LlmOptions;
import com.utm.temporal.llm.Message;
import com.utm.temporal.llm.OpenAiLlmClient;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.TestSummary;
import com.utm.temporal.util.PromptLoader;

import java.util.Arrays;
import java.util.List;

/**
 * Test Quality Agent - assesses whether the PR is adequately tested.
 * Applies strict rules about test coverage and suggests high-value tests.
 */
public class TestQualityAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public TestQualityAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public TestQualityAgent() {
        this.llmClient = new OpenAiLlmClient();
        this.objectMapper = new ObjectMapper();
    }

    public AgentResult analyze(String prTitle, String prDescription, String diff, TestSummary testSummary) {
        try {
            // If testSummary is null, create a default one
            if (testSummary == null) {
                testSummary = new TestSummary(true, 0, 0, 0);
            }

            // Rule 1: If tests failed, BLOCK immediately
            if (!testSummary.passed) {
                return new AgentResult(
                    "Test Quality",
                    "HIGH",
                    "BLOCK",
                    Arrays.asList(
                        "Tests are failing - " + testSummary.failedTests + " out of " + testSummary.totalTests + " tests failed",
                        "All tests must pass before the PR can be approved",
                        "Fix the failing tests and ensure the build is green"
                    )
                );
            }

            // Build system prompt with rules
            String systemPrompt = buildSystemPrompt(testSummary);

            // Build user prompt with PR details
            String userPrompt = String.format(
                "PR Title: %s\n\nPR Description: %s\n\nDiff:\n%s\n\nTest Summary:\n- Passed: %s\n- Total Tests: %d\n- Failed Tests: %d\n- Duration: %d ms",
                prTitle,
                prDescription,
                diff,
                testSummary.passed,
                testSummary.totalTests,
                testSummary.failedTests,
                testSummary.durationMs
            );

            // Call LLM
            List<Message> messages = Arrays.asList(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            );

            LlmOptions options = new LlmOptions(
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"),
                0.2,
                "json_object"
            );

            String response = llmClient.chat(messages, options);

            // Parse JSON response into AgentResult
            return objectMapper.readValue(response, AgentResult.class);

        } catch (Exception e) {
            // Fail fast - no retry logic!
            throw new RuntimeException("Test Quality Agent failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(TestSummary testSummary) {
        return PromptLoader.loadPrompt("test-quality");
    }
}
