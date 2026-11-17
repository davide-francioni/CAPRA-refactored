package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * The blueprint: which model answers for which role, in this execution.
 * <p>
 * Read from an external file indicated at startup, so that the benchmarking platform
 * can drive many executions with different configurations without recompiling:
 * <pre>
 *     java -jar capra.jar --spring.config.additional-location=file:/path/blueprint.yaml
 * </pre>
 *
 * @param id                identifier of the execution, archived with the results
 * @param description       free text
 * @param interventionLevel pure metadata: read and logged
 * @param defaults          applied to every role not overridden
 * @param roles             per-role overrides, merged field by field over the defaults
 * @param retry             retry policy, archived with the run
 */
// ignoreUnknownFields = false: an unrecognised key under `blueprint:` fails the startup

@ConfigurationProperties(prefix = "blueprint", ignoreUnknownFields = false)
public record BlueprintProperties(
        String id,
        String description,
        String interventionLevel,
        RoleConfig defaults,
        Map<LlmRole, RoleConfig> roles,
        RetryPolicy retry
) {

    /**
     * The effective configuration for a role: the defaults, overridden field by field
     * by the entry for that role. A role absent from the overrides uses the defaults
     * whole.
     *
     * @param role the role to resolve
     * @return the merged configuration, or {@code null} if nothing is configured at all
     */
    public RoleConfig resolve(LlmRole role) {
        RoleConfig override = roles == null ? null : roles.get(role);
        return override == null ? defaults : override.mergedOver(defaults);
    }

    /** The retry policy, falling back to the benchmark default when unspecified. */
    public RetryPolicy retryOrDefault() {
        return retry != null ? retry : RetryPolicy.benchmarkDefault();
    }

    /**
     * Every role with its resolved configuration.
     * <p>
     * Used at startup to build the clients and to fail fast on an incomplete blueprint.
     */
    public Map<LlmRole, RoleConfig> resolveAll() {
        Map<LlmRole, RoleConfig> resolved = new EnumMap<>(LlmRole.class);
        for (LlmRole role : LlmRole.values()) {
            RoleConfig config = resolve(role);
            if (config != null) {
                resolved.put(role, config);
            }
        }
        return resolved;
    }
}
