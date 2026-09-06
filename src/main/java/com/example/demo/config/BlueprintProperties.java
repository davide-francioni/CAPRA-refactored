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
 * @param interventionLevel pure metadata: read and logged, nothing else. It exists
 *                          because it is archived with the run, and that is what makes
 *                          a result attributable — otherwise a results file would say
 *                          «model X failed» without saying which prompts it was using
 * @param defaults          applied to every role not overridden
 * @param roles             per-role overrides, merged field by field over the defaults
 * @param prompts           system prompts per role; absent means «use the built-in one»
 * @param retry             retry policy, archived with the run rather than hidden in code
 */
@ConfigurationProperties(prefix = "blueprint")
public record BlueprintProperties(
        String id,
        String description,
        String interventionLevel,
        RoleConfig defaults,
        Map<LlmRole, RoleConfig> roles,
        Map<LlmRole, String> prompts,
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

    /**
     * The externalised system prompt for a role, or {@code null} to use the one
     * compiled into the agent.
     * <p>
     * Needed from intervention level L1 onwards, where prompts are rewritten for
     * smaller models. Returning {@code null} at L0 keeps the original behaviour
     * exactly, which is what the baseline requires.
     */
    public String promptFor(LlmRole role) {
        return prompts == null ? null : prompts.get(role);
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
