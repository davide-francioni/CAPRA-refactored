package com.example.demo.model;

import java.util.List;
import java.util.Map;

/**
 * Everything one execution produced, in a single response.
 * <p>
 * The system exposes two other endpoints: one returns the compiled document but not the structured report,
 * the other the structured report but compiles nothing. Calling both would run the
 * models twice on the same document, double the cost and the time
 * <p>
 * Hence this payload, which is what allows a document to be processed once.
 *
 * @param report       the structured report
 * @param texGenerated whether the LaTeX role produced a usable source
 * @param pdfGenerated whether the source compiled
 * @param latexSource  the generated source
 * @param compileError diagnostic detail when compilation failed
 * @param retryEvents  every failed attempt, with its cause classified
 * @param tokenUsage   what the execution consumed, broken down by role
 * @param servedBy     which endpoint answered for each model, as the aggregator reported
 *                     it. Keyed by model, because a client is shared by every role
 *                     carrying the same configuration
 */
public record AuditRunResponse(
        AuditReport report,
        boolean texGenerated,
        boolean pdfGenerated,
        String latexSource,
        String compileError,
        List<RetryEvent> retryEvents,
        TokenUsage tokenUsage,
        Map<String, String> servedBy
) {
    /**
     * Failed attempts attributable to the infrastructure rather than to the model.
     */
    public long infrastructuralRetries() {
        return retryEvents == null ? 0
                : retryEvents.stream().filter(RetryEvent::isInfrastructural).count();
    }
}
