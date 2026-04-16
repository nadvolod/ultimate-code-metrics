package com.utm.temporal.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised application configuration read from environment variables.
 *
 * <p>Call {@link #validate()} once at startup to fail fast with clear error
 * messages when required variables are missing or invalid.
 *
 * <p>After {@code validate()} returns, all getter methods return cached values
 * that were resolved at startup.  They will never read from
 * {@code System.getenv()} again, which is critical for Temporal workflow
 * determinism &mdash; environment variables can differ across replays.
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

    // Cached values resolved once at startup via validate().
    // Temporal workflows must be deterministic — they must never read
    // environment variables directly because env values can differ across
    // replays.  By resolving and caching at worker-startup time we
    // guarantee that every replay sees the same configuration.
    private static volatile String temporalAddress;
    private static volatile String taskQueue;
    private static volatile String openAiModel;
    private static volatile boolean dummyMode;
    private static volatile int activityTimeoutSeconds;
    private static volatile int retryIntervalSeconds;
    private static volatile boolean validated = false;

    private AppConfig() {
        // utility class
    }

    /**
     * Returns the cached Temporal server address.
     * Must call {@link #validate()} once before using this method.
     */
    public static String getTemporalAddress() {
        ensureValidated();
        return temporalAddress;
    }

    /**
     * Returns the cached task queue name.
     * Must call {@link #validate()} once before using this method.
     */
    public static String getTaskQueue() {
        ensureValidated();
        return taskQueue;
    }

    /**
     * Returns the cached OpenAI model name.
     * Must call {@link #validate()} once before using this method.
     */
    public static String getOpenAiModel() {
        ensureValidated();
        return openAiModel;
    }

    /**
     * Returns whether dummy mode is enabled (cached).
     * Must call {@link #validate()} once before using this method.
     */
    public static boolean isDummyMode() {
        ensureValidated();
        return dummyMode;
    }

    /**
     * Returns the cached activity start-to-close timeout in seconds.
     * Must call {@link #validate()} once before using this method.
     */
    public static int getActivityTimeoutSeconds() {
        ensureValidated();
        return activityTimeoutSeconds;
    }

    /**
     * Returns the cached activity initial retry interval in seconds.
     * Must call {@link #validate()} once before using this method.
     */
    public static int getRetryIntervalSeconds() {
        ensureValidated();
        return retryIntervalSeconds;
    }

    /**
     * Resolves all environment variables, validates them, and caches the
     * results.  Must be called exactly once at worker startup (in
     * {@code WorkerApp.main}) before any Temporal workflow or activity
     * code runs.
     *
     * <p>After this method returns, all {@code get*()} methods return the
     * cached values &mdash; they will never read from {@code System.getenv()}
     * again, which is critical for Temporal workflow determinism.
     *
     * @throws IllegalStateException if any required variable is missing or any value is invalid
     */
    public static void validate() {
        List<String> errors = new ArrayList<>();

        // Resolve all values from the environment once
        boolean resolvedDummyMode = "true".equalsIgnoreCase(
                System.getenv().getOrDefault("DUMMY_MODE", "false"));

        // OPENAI_API_KEY is required unless DUMMY_MODE is enabled
        if (!resolvedDummyMode) {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                errors.add("OPENAI_API_KEY is required when DUMMY_MODE is not enabled");
            }
        }

        // Validate and resolve numeric values
        int resolvedTimeout = DEFAULT_ACTIVITY_TIMEOUT_SECONDS;
        try {
            resolvedTimeout = parseIntEnv("ACTIVITY_TIMEOUT_SECONDS", DEFAULT_ACTIVITY_TIMEOUT_SECONDS);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        int resolvedRetry = DEFAULT_RETRY_INTERVAL_SECONDS;
        try {
            resolvedRetry = parseIntEnv("ACTIVITY_RETRY_INTERVAL_SECONDS", DEFAULT_RETRY_INTERVAL_SECONDS);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            System.err.println("Configuration errors found at startup:");
            errors.forEach(msg -> System.err.println("  - " + msg));
            throw new IllegalStateException(
                    "Startup configuration validation failed: " + String.join("; ", errors));
        }

        // Cache all resolved values — these are now immutable for the
        // lifetime of the JVM, keeping Temporal replays deterministic.
        temporalAddress = getTrimmedEnvOrDefault("TEMPORAL_ADDRESS", DEFAULT_TEMPORAL_ADDRESS);
        taskQueue = getTrimmedEnvOrDefault("TASK_QUEUE", DEFAULT_TASK_QUEUE);
        openAiModel = getTrimmedEnvOrDefault("OPENAI_MODEL", DEFAULT_OPENAI_MODEL);
        dummyMode = resolvedDummyMode;
        activityTimeoutSeconds = resolvedTimeout;
        retryIntervalSeconds = resolvedRetry;
        validated = true;

        // Log resolved configuration
        System.out.println("Configuration:");
        System.out.println("  TEMPORAL_ADDRESS              : " + temporalAddress);
        System.out.println("  TASK_QUEUE                    : " + taskQueue);
        System.out.println("  ACTIVITY_TIMEOUT_SECONDS      : " + activityTimeoutSeconds);
        System.out.println("  ACTIVITY_RETRY_INTERVAL_SECONDS: " + retryIntervalSeconds);
        System.out.println("  OPENAI_MODEL                  : " + openAiModel);
    }

    private static void ensureValidated() {
        if (!validated) {
            throw new IllegalStateException(
                    "AppConfig.validate() must be called at startup before reading configuration");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the trimmed value of the given environment variable, or
     * {@code defaultValue} when the variable is unset or blank.
     */
    private static String getTrimmedEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

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
