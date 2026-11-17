package com.example.demo.model;

/**
 * Represents a single issue detected during the document audit.
 *
 * @param id              Unique identifier (e.g. REQ-001, TST-001)
 * @param severity        Severity level
 * @param description     Detailed description of the issue
 * @param pageReference   Page number in the document where the issue appears
 * @param quote           Verbatim quote from the original document
 * @param category        Issue category (Requirements, Architecture, Testing)
 * @param recommendation  Suggested corrective action for the student
 * @param confidenceScore Agent confidence level (0.0-1.0), as declared by the model.
 */
public record AuditIssue(
        String id,
        Severity severity,
        String description,
        int pageReference,
        String quote,
        String category,
        String recommendation,
        double confidenceScore
) {
    /**
     * No default is applied to {@code confidenceScore}.
     * <p>
     * The original version raised any value of 0.0 or below to 0.8. {@code double} is a
     * primitive, so a field the model omits arrives as 0.0 rather than null, and without
     * a default a finding would be discarded because of a missing field.
     * <p>
     * But the default makes "the model did not emit the field" indistinguishable from "the
     * model declared 0.8". Emitting a numeric field correctly in structured JSON is the first
     * capability to degrade asmodels get smaller, precisely the failure mode under study.
     * Worse, the default pushes in the model's favour: 0.8 survives every anchoring penalty
     * except the harshest, so a model that ignores the field is advantaged over one that fills
     * it, and the confidence filter ends up applying one criterion to the reference and another
     * to the candidate.
     * <p>
     * Leaving 0.0 is the correct behaviour: a finding whose confidence nobody produced has
     * no claim on the report. The consequence is deliberate — a model that never emits the
     * field has all its findings filtered out, which is a genuine failure of format and
     * should be recorded as one.
     */
    public AuditIssue {
        // Intentionally empty: see the note above.
    }

    /** Creates a copy with a new ID. */
    public AuditIssue withId(String newId) {
        return new AuditIssue(newId, severity, description, pageReference, quote, category, recommendation, confidenceScore);
    }

    /** Creates a copy with a new confidence score. */
    public AuditIssue withConfidence(double newConfidence) {
        return new AuditIssue(id, severity, description, pageReference, quote, category, recommendation, newConfidence);
    }
}
