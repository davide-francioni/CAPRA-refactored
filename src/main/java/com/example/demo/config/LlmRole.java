package com.example.demo.config;

/**
 * The six points at which CAPRA consumes a language model.
 * <p>
 * This enum is the unit of configuration for the whole system, and the contract with
 * the benchmarking platform that drives it. The choice of the <em>role</em> as the
 * unit — rather than the calling class or the provider — is deliberate: a
 * configuration expressed in terms of classes would break at the first internal
 * reorganisation, and one expressed in terms of providers could not distinguish six
 * models served by the same endpoint.
 * <p>
 * As long as the system keeps performing these six tasks, existing blueprints stay
 * valid even if the code around them changes.
 */
public enum LlmRole {

    /** Requirements and use-case analysis. Called {@code SpecificationAuditorAgent} in the paper. */
    REQUIREMENTS_AGENT,

    /** Test coverage audit. */
    TEST_AUDITOR_AGENT,

    /** Expected-feature coverage check, backed by the MongoDB knowledge base. */
    FEATURE_CHECK_AGENT,

    /** Use case to design to test traceability matrix. */
    TRACEABILITY_MATRIX_AGENT,

    /** Meta-agent: verification, deduplication and renumbering of candidate issues. */
    CONSISTENCY_MANAGER,

    /** LaTeX report generation. The only role served by Anthropic in the original setup. */
    LATEX_REPORT
}
