package com.example.demo.config;

/**
 * How many times a failed call is retried, and at which layer
 * <p>
 * The system has two retry mechanisms stacked: the application-level one in
 * {@code ResilientLlmCaller}, and Spring AI's transport retry underneath. Left both
 * active, a single logical call can become many more real requests than expected
 * <p>
 * In benchmark mode the transport retry is disabled
 * <p>
 * This is a configuration field rather than a constant so that the choice is recorded
 * alongside the results
 *
 * @param springAiEnabled        transport-level retry; {@code false} in benchmark mode
 * @param applicationMaxAttempts total attempts, including the first
 */
public record RetryPolicy(
        boolean springAiEnabled,
        int applicationMaxAttempts
) {
    /** The benchmark default: no transport retry, three application attempts. */
    public static RetryPolicy benchmarkDefault() {
        return new RetryPolicy(false, 3);
    }

    public int effectiveAttempts() {
        return applicationMaxAttempts > 0 ? applicationMaxAttempts : 3;
    }
}
