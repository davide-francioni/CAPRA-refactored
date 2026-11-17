package com.example.demo.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How one role reaches its model.
 * <p>
 * Being a record is what allows
 * {@link ChatClientRegistry} to build a single shared client when several roles carry
 * an identical configuration
 *
 * @param provider       which client to build
 * @param baseUrl        endpoint of the aggregator, or of the commercial API for the baseline
 * @param apiKey         real key for the commercial APIs, a placeholder otherwise
 * @param model          model identifier as the endpoint expects it
 * @param timeoutSeconds budget for a single HTTP request, not for the whole
 *                       logical call
 * @param routing        which endpoint must serve this role, when the model is reached
 *                       through an aggregator
 * @param options        model options passed through as-is (temperature, seed, max tokens…)
 */
public record RoleConfig(
        Provider provider,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        Routing routing,
        Map<String, Object> options
) {

    /**
     * Which endpoint must answer, when the model is served by an aggregator
     *
     * @param order          endpoint tags in order of preference, as the aggregator names
     *                       them
     * @param allowFallbacks whether the aggregator may route elsewhere when no endpoint in
     *                       {@code order} is available. <b>Defaults to false</b>
     */
    public record Routing(List<String> order, Boolean allowFallbacks) {

        /** Whether this actually pins anything */
        public boolean pins() {
            return order != null && !order.isEmpty();
        }

        /** False unless the blueprint says otherwise */
        public boolean fallbacksAllowed() {
            return Boolean.TRUE.equals(allowFallbacks);
        }
    }

    /**
     * Merges this configuration over a set of defaults
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
                routing != null && routing.pins() ? routing : defaults.routing(),
                mergedOptions
        );
    }
}
