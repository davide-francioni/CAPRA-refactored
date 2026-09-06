package com.example.demo.config;

/**
 * How many times a failed call is retried, and at which layer.
 * <p>
 * The system has two retry mechanisms stacked: the application-level one in
 * {@code ResilientLlmCaller}, and Spring AI's transport retry underneath. Left both
 * active, a single logical call can become many more real requests than expected.
 * <p>
 * In benchmark mode the transport retry is disabled. The reason is not only cleaner
 * accounting: transport retry <em>absorbs infrastructure problems</em> and reports
 * them as successes, which is precisely what has to remain distinguishable from the
 * model being unable to do the job.
 * <p>
 * This is a configuration field rather than a constant so that the choice is recorded
 * alongside the results instead of being buried in the code.
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

    /** Guards against a misconfigured value silently disabling retries altogether. */
    public int effectiveAttempts() {
        return applicationMaxAttempts > 0 ? applicationMaxAttempts : 3;
    }
}
