package com.example.demo.config;

/**
 * Why a single model invocation failed
 */
public enum RetryCause {

    /** The endpoint was unreachable */
    CONNECTION_ERROR,

    /** No response within the per-request budget */
    TIMEOUT,

    /** The server answered with an error status */
    HTTP_ERROR,

    /** A response arrived but could not be parsed into the expected structure
     *  This one is attributable to the model. */
    PARSE_ERROR;

    /** Whether this cause points at the infrastructure rather than at the model */
    public boolean isInfrastructural() {
        return this != PARSE_ERROR;
    }
}
