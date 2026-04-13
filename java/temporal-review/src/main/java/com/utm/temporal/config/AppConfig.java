package com.utm.temporal.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised application configuration read from environment variables.
 *
 * <p>Call {@link #validate()} once at startup to fail fast with clear error
 * messages when required variables are missing or invalid.
 *
 * <p>Supported environment variables:
 * <ul>
 *   <li>{@code OPENAI_API_KEY} — required unless {@code DUMMY_MODE=true}</li>
 *   <li>{@code OPENAI_MODEL} — optional, defaults to {@value DEFAULT_OPENAI_MODEL}</li>
 *   <li>{@code TEMPORAL_ADDRESS} — optional, defaults to {@value DEFAULT_TEMPORAL_ADDRESS}</li>
 *   <li>{@code TASK_QUEUE} — optional, defaults to {@value DEFAULT_TASK_QUEUE}</li>
 *   <li>{@code ACTIVITY_TIMEOUT_SECONDS} — optional, defaults to {@value DEFAULT_ACTIVITY_TIMEOUT_SECONDS}</li>
 *   <li>{@code ACTIVITY_RETRY_INTERVAL_SECONDS} — optional, defaults to {@value DEFAULT_RETRY_INTERVAL_SECONDS}</li>
 *   <li>{@code DUMMY_MODE} — optional, set to {@code true} to skip real LLM calls</li>
 * </ul>
 */
public class AppConfig {

    public static final String DEFAULT_TEMPORAL_ADDRESS = "localhost:7233";
    public static final String DEFAULT_TASK_QUEUE = "pr-review";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5.4-mini";
    public static final int DEFAULT_ACTIVITY_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_RETRY_INTERVAL_SECONDS = 5;

    private AppConfig() {
        // utility class
    }

    public static String getTemporalAddress() {
        return System.getenv().getOrDefault("TEMPORAL_ADDRESS", DEFAULT_TEMPORAL_ADDRESS);
    }

    public static String getTaskQueue() {
        return System.getenv().getOrDefault("TASK_QUEUE", DEFAULT_TASK_QUEUE);
    }

    public static String getOpenAiModel() {
        return System.getenv().getOrDefault("OPENAI_MODEL", DEFAULT_OPENAI_MODEL);
    }

    public static boolean isDummyMode() {
        return "true".equalsIgnoreCase(System.getenv().getOrDefault("DUMMY_MODE", "false"));
    }

    /**
     * Returns the activity start-to-close timeout in seconds.
     * Read from {@code ACTIVITY_TIMEOUT_SECONDS}; defaults to {@value DEFAULT_ACTIVITY_TIMEOUT_SECONDS}.
     *
     * @throws IllegalArgumentException if the env var is set but not a valid integer
     */
    public static int getActivityTimeoutSeconds() {
        return parseIntEnv("ACTIVITY_TIMEOUT_SECONDS", DEFAULT_ACTIVITY_TIMEOUT_SECONDS);
    }

    /**
     * Returns the activity initial retry interval in seconds.
     * Read from {@code ACTIVITY_RETRY_INTERVAL_SECONDS}; defaults to {@value DEFAULT_RETRY_INTERVAL_SECONDS}.
     *
     * @throws IllegalArgumentException if the env var is set but not a valid integer
     */
    public static int getRetryIntervalSeconds() {
        return parseIntEnv("ACTIVITY_RETRY_INTERVAL_SECONDS", DEFAULT_RETRY_INTERVAL_SECONDS);
    }

    /**
     * Validates all required environment variables and fails fast with clear error
     * messages when the configuration is invalid.
     *
     * @throws IllegalStateException if any required variable is missing or any value is invalid
     */
    public static void validate() {
        List<String> errors = new ArrayList<>();

        // OPENAI_API_KEY is required unless DUMMY_MODE is enabled
        if (!isDummyMode()) {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                errors.add("OPENAI_API_KEY is required when DUMMY_MODE is not enabled");
            }
        }

        // Validate numeric values
        try {
            getActivityTimeoutSeconds();
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        try {
            getRetryIntervalSeconds();
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            System.err.println("Configuration errors found at startup:");
            errors.forEach(msg -> System.err.println("  - " + msg));
            throw new IllegalStateException(
                    "Startup configuration validation failed: " + String.join("; ", errors));
        }

        // Log resolved configuration
        System.out.println("Configuration:");
        System.out.println("  TEMPORAL_ADDRESS              : " + getTemporalAddress());
        System.out.println("  TASK_QUEUE                    : " + getTaskQueue());
        System.out.println("  ACTIVITY_TIMEOUT_SECONDS      : " + getActivityTimeoutSeconds());
        System.out.println("  ACTIVITY_RETRY_INTERVAL_SECONDS: " + getRetryIntervalSeconds());
        System.out.println("  OPENAI_MODEL                  : " + getOpenAiModel());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int parseIntEnv(String name, int defaultValue) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(
                        name + " must be a positive integer, got: " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " must be a valid integer, got: " + raw);
        }
    }
}
