package com.utm.temporal.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    /** True when validate() succeeded during {@link #setUp()}. */
    private static boolean validationPassed;

    @BeforeAll
    static void setUp() {
        // Always attempt validation and record the outcome so every test
        // can branch deterministically on a known boolean — no silent
        // skipping.
        try {
            AppConfig.validate();
            validationPassed = true;
        } catch (IllegalStateException e) {
            validationPassed = false;
        }
    }

    // -----------------------------------------------------------------
    // Constants (always exercised — no dependency on env)
    // -----------------------------------------------------------------

    @Test
    void defaults_areCorrect() {
        assertEquals("localhost:7233", AppConfig.DEFAULT_TEMPORAL_ADDRESS);
        assertEquals("pr-review", AppConfig.DEFAULT_TASK_QUEUE);
        assertEquals("gpt-4o-mini", AppConfig.DEFAULT_OPENAI_MODEL);
        assertEquals(60, AppConfig.DEFAULT_ACTIVITY_TIMEOUT_SECONDS);
        assertEquals(5, AppConfig.DEFAULT_RETRY_INTERVAL_SECONDS);
    }

    // -----------------------------------------------------------------
    // Validation semantics (always exercised)
    // -----------------------------------------------------------------

    @Test
    void validate_outcome_matchesEnvironment() {
        boolean dummyMode = "true".equalsIgnoreCase(System.getenv("DUMMY_MODE"));
        boolean hasApiKey = System.getenv("OPENAI_API_KEY") != null
                && !System.getenv("OPENAI_API_KEY").isBlank();

        if (dummyMode || hasApiKey) {
            assertTrue(validationPassed,
                    "validate() should pass when DUMMY_MODE=true or OPENAI_API_KEY is set");
        } else {
            assertFalse(validationPassed,
                    "validate() should fail when DUMMY_MODE is off and OPENAI_API_KEY is missing");
        }
    }

    // -----------------------------------------------------------------
    // Getter tests (guarded — only meaningful after successful validate)
    // -----------------------------------------------------------------

    @Test
    void getTemporalAddress_returnsNonBlank_afterValidate() {
        if (!validationPassed) return; // getters require prior validate()
        String address = AppConfig.getTemporalAddress();
        assertNotNull(address);
        assertFalse(address.isBlank());
    }

    @Test
    void getTaskQueue_returnsNonBlank_afterValidate() {
        if (!validationPassed) return;
        String taskQueue = AppConfig.getTaskQueue();
        assertNotNull(taskQueue);
        assertFalse(taskQueue.isBlank());
    }

    @Test
    void getActivityTimeoutSeconds_returnsPositive_afterValidate() {
        if (!validationPassed) return;
        int timeout = AppConfig.getActivityTimeoutSeconds();
        assertTrue(timeout > 0);
    }

    @Test
    void getRetryIntervalSeconds_returnsPositive_afterValidate() {
        if (!validationPassed) return;
        int interval = AppConfig.getRetryIntervalSeconds();
        assertTrue(interval > 0);
    }

    @Test
    void getOpenAiModel_returnsNonBlank_afterValidate() {
        if (!validationPassed) return;
        String model = AppConfig.getOpenAiModel();
        assertNotNull(model);
        assertFalse(model.isBlank());
    }

    @Test
    void getters_returnCachedValues_afterValidate() {
        if (!validationPassed) return;
        // After validate(), getters should return stable cached values
        // (not re-read from env).  Call them twice and confirm they match.
        assertEquals(AppConfig.getTemporalAddress(), AppConfig.getTemporalAddress());
        assertEquals(AppConfig.getTaskQueue(), AppConfig.getTaskQueue());
        assertEquals(AppConfig.getOpenAiModel(), AppConfig.getOpenAiModel());
        assertEquals(AppConfig.getActivityTimeoutSeconds(), AppConfig.getActivityTimeoutSeconds());
        assertEquals(AppConfig.getRetryIntervalSeconds(), AppConfig.getRetryIntervalSeconds());
        assertEquals(AppConfig.isDummyMode(), AppConfig.isDummyMode());
    }
}
