package com.example.demo.config;

import java.util.HashMap;
import java.util.Map;

/**
 * How one role reaches its model.
 * <p>
 * Being a record, equality is by value: this is what allows
 * {@link ChatClientRegistry} to build a single shared client when several roles carry
 * an identical configuration, instead of opening one connection pool per role.
 *
 * @param provider       which client to build
 * @param baseUrl        endpoint; for local models, the address of the serving machine
 * @param apiKey         real key for the commercial APIs, a placeholder otherwise
 * @param model          model identifier as the endpoint expects it
 * @param timeoutSeconds budget for a <em>single</em> HTTP request, not for the whole
 *                       logical call: with three application attempts the worst case
 *                       is three times this value plus the backoffs
 * @param options        model options passed through as-is (temperature, seed, …)
 */
public record RoleConfig(
        Provider provider,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        Map<String, Object> options
) {

    /**
     * Merges this configuration over a set of defaults, field by field.
     * <p>
     * The merge is shallow and per-field, not per-block: a role that overrides only
     * the base URL keeps the defaults for everything else. It is what keeps blueprint
     * files short, since in the typical case only address and model identifier change.
     *
     * @param defaults the blueprint-wide defaults; may be {@code null}
     * @return the effective configuration for the role
     */
    public RoleConfig mergedOver(RoleConfig defaults) {
        if (defaults == null) {
            return this;
        }
        Map<String, Object> mergedOptions = new HashMap<>();
        if (defaults.options() != null) {
            mergedOptions.putAll(defaults.options());
        }
        if (options != null) {
            mergedOptions.putAll(options);
        }
        return new RoleConfig(
                provider != null ? provider : defaults.provider(),
                baseUrl != null ? baseUrl : defaults.baseUrl(),
                apiKey != null ? apiKey : defaults.apiKey(),
                model != null ? model : defaults.model(),
                timeoutSeconds > 0 ? timeoutSeconds : defaults.timeoutSeconds(),
                mergedOptions
        );
    }
}
