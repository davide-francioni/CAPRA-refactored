package com.example.demo.model;

import com.example.demo.config.LlmRole;
import com.example.demo.config.RetryCause;

import java.time.Instant;

/**
 * One failed model invocation, with its cause classified.
 *
 * @param role          which role was calling
 * @param attemptNumber 1 for the first attempt
 * @param cause         classified reason for the failure
 * @param detail        message from the underlying error, for diagnosis
 * @param timestamp     when the attempt failed
 */
public record RetryEvent(
        LlmRole role,
        int attemptNumber,
        RetryCause cause,
        String detail,
        Instant timestamp
) {
    public static RetryEvent of(LlmRole role, int attemptNumber, RetryCause cause, String detail) {
        return new RetryEvent(role, attemptNumber, cause, detail, Instant.now());
    }

    /** Whether this failure points at the infrastructure rather than at the model. */
    public boolean isInfrastructural() {
        return cause != null && cause.isInfrastructural();
    }
}
