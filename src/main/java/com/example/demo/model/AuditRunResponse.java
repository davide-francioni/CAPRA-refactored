package com.example.demo.model;

import java.util.List;

/**
 * Everything one execution produced, in a single response.
 * <p>
 * The system exposes two other endpoints, and neither provides what an automated
 * evaluation needs: one returns the compiled document but not the structured report,
 * the other the structured report but compiles nothing. Calling both would run the
 * models <em>twice on the same document</em> — double the cost and the time, and,
 * without guaranteed determinism, two outcomes that need not agree with each other,
 * which would make the comparison meaningless.
 * <p>
 * Hence this payload, which is what allows a document to be processed once.
 *
 * @param report       the structured report
 * @param texGenerated whether the LaTeX role produced a usable source
 * @param pdfGenerated whether the source compiled
 * @param latexSource  the generated source, returned inline so that the caller need not
 *                     read files from a directory this system manages for itself
 * @param compileError diagnostic detail when compilation failed; it explains, it does
 *                     not decide
 * @param retryEvents  every failed attempt, with its cause classified. What allows an
 *                     infrastructure problem to be told apart from the model being
 *                     unable to do the job
 */
public record AuditRunResponse(
        AuditReport report,
        boolean texGenerated,
        boolean pdfGenerated,
        String latexSource,
        String compileError,
        List<RetryEvent> retryEvents
) {
    /**
     * Failed attempts attributable to the infrastructure rather than to the model.
     * <p>
     * A run with a non-zero count here deserves to be examined before its verdict is
     * taken at face value.
     */
    public long infrastructuralRetries() {
        return retryEvents == null ? 0
                : retryEvents.stream().filter(RetryEvent::isInfrastructural).count();
    }
}
