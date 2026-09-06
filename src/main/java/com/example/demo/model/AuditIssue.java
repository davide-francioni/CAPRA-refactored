package com.example.demo.model;

/**
 * Represents a single issue detected during the document audit.
 * <p>
 * <b>The confidence score is deliberately nullable, and no default is applied.</b>
 * <p>
 * An earlier version assigned 0.8 whenever the value was missing or non-positive.
 * That default sits <em>above</em> the filtering threshold applied downstream, so a
 * model that never emitted the field at all would see its issues pass the filter with
 * a score no model had produced. A failure to comply with the output format would thus
 * disguise itself as a success — and the effect grows as the model gets smaller, that
 * is precisely in the region this work is investigating.
 * <p>
 * Removing the default is not by itself sufficient: with a primitive type, «not
 * emitted» and «emitted as zero» remain indistinguishable, which is the very ambiguity
 * the default was masking. Hence the wrapper type, and hence
 * {@link #hasConfidence()} for call sites that need to tell the two apart.
 *
 * @param id              Unique identifier (e.g. REQ-001, TST-001)
 * @param severity        Severity level
 * @param description     Detailed description of the issue
 * @param pageReference   Page number in the document where the issue appears
 * @param quote           Verbatim quote from the original document
 * @param category        Issue category (Requirements, Architecture, Testing)
 * @param recommendation  Suggested corrective action for the student
 * @param confidenceScore Agent confidence (0.0-1.0), or {@code null} when the model
 *                        did not emit the field. Never defaulted.
 */
public record AuditIssue(
        String id,
        Severity severity,
        String description,
        int pageReference,
        String quote,
        String category,
        String recommendation,
        Double confidenceScore
) {

    /** Whether the model actually emitted a confidence score for this issue. */
    public boolean hasConfidence() {
        return confidenceScore != null;
    }

    /**
     * The confidence as a primitive, treating an absent value as zero.
     * <p>
     * For arithmetic and for the filtering threshold. Zero is the conservative reading:
     * an issue whose confidence the model never stated does not get the benefit of the
     * doubt, whereas the previous default granted it more confidence than most issues
     * that were scored explicitly.
     */
    public double confidenceOrZero() {
        return confidenceScore != null ? confidenceScore : 0.0;
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
