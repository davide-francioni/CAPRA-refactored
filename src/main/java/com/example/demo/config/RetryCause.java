package com.example.demo.config;

/**
 * Why a single model invocation failed.
 * <p>
 * This classification is what makes it acceptable to have disabled the transport-level
 * retry. Without it, a momentary network interruption would be recorded as «this model
 * cannot do the job» — which is precisely the kind of error that invalidates a
 * measurement.
 * <p>
 * The first three point at the infrastructure, the last at the model.
 */
public enum RetryCause {

    /** The endpoint was unreachable. */
    CONNECTION_ERROR,

    /** No response within the per-request budget. */
    TIMEOUT,

    /** The server answered with an error status. On a local engine this usually means
     *  the model ran out of memory or the process died. */
    HTTP_ERROR,

    /** A response arrived but could not be parsed into the expected structure.
     *  This one is attributable to the model. */
    PARSE_ERROR;

    /** Whether this cause points at the infrastructure rather than at the model. */
    public boolean isInfrastructural() {
        return this != PARSE_ERROR;
    }
}
