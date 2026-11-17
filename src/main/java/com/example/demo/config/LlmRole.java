package com.example.demo.config;

/**
 * The seven points at which CAPRA consumes a language model.
 * <p>
 * <b>The same enumeration exists in the Amaltea</b>, and nothing keeps the
 * two copies identical automatically. ChatClientRegistry fails startup on any role
 * left unconfigured. An automatic control or synchronization between the two systems could
 * be a future upgrade of the software
 */
public enum LlmRole {

    /** Requirements and use-case analysis. Called SpecificationAuditorAgent in the paper. */
    REQUIREMENTS_AGENT,

    TEST_AUDITOR_AGENT,
    FEATURE_CHECK_AGENT,
    TRACEABILITY_MATRIX_AGENT,
    GLOSSARY_CONSISTENCY_AGENT,
    CONSISTENCY_MANAGER,
    LATEX_REPORT
}
