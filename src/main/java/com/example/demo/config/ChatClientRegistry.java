package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the chat client of each role.
 * <p>
 * Replaces the fixed, named beans of the original configuration: consumers no longer
 * receive a client selected by name at compile time, but ask for the one associated
 * with their role. It is what allows six independent models where there used to be
 * two fixed ones.
 * <p>
 * Clients are built once at startup and reused for the whole lifetime of the process.
 * Since a process serves exactly one blueprint, the configuration cannot change under
 * them.
 */
@Component
public class ChatClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

    private final Map<LlmRole, ChatClient> clients = new EnumMap<>(LlmRole.class);
    private final BlueprintProperties blueprint;

    public ChatClientRegistry(BlueprintProperties blueprint, ChatClientFactory factory) {
        this.blueprint = blueprint;

        Map<LlmRole, RoleConfig> resolved = blueprint.resolveAll();
        failOnIncompleteBlueprint(resolved);

        // One client per DISTINCT configuration, not per role. In the baseline five
        // roles out of six point at the same model: without this, five connection
        // pools would be opened towards the same endpoint for no reason.
        // Records give equality by value, so the configuration itself is the key.
        Map<RoleConfig, ChatClient> shared = new HashMap<>();

        resolved.forEach((role, config) -> {
            ChatClient client = shared.computeIfAbsent(config, factory::create);
            clients.put(role, client);
        });

        log.info("Blueprint '{}' [{}]: {} roles served by {} distinct clients",
                blueprint.id(), blueprint.interventionLevel(),
                clients.size(), shared.size());

        resolved.forEach((role, config) ->
                log.info("  {} -> {} @ {}", role, config.model(), config.baseUrl()));
    }

    /**
     * The client assigned to a role.
     *
     * @throws IllegalStateException if the blueprint declares no configuration for it
     */
    public ChatClient clientFor(LlmRole role) {
        ChatClient client = clients.get(role);
        if (client == null) {
            throw new IllegalStateException(
                    "No client configured for role " + role + " in blueprint '" + blueprint.id() + "'");
        }
        return client;
    }

    /**
     * The externalised system prompt for a role, or {@code null} to use the built-in one.
     * <p>
     * Exposed here so that consumers have a single point of contact for everything the
     * blueprint decides about them, instead of receiving the registry and the
     * properties separately.
     */
    public String promptFor(LlmRole role) {
        return blueprint.promptFor(role);
    }

    /** The retry policy in force for this execution. */
    public RetryPolicy retryPolicy() {
        return blueprint.retryOrDefault();
    }

    /** Roles the blueprint actually configures. */
    public Set<LlmRole> configuredRoles() {
        return clients.keySet();
    }

    /**
     * Fails startup if any role is left unconfigured.
     * <p>
     * The check matters more than it appears. The role enumeration is duplicated in
     * the benchmarking platform, and nothing keeps the two copies in step
     * automatically: a role added on one side only would otherwise produce a blueprint
     * carrying a key this system ignores, and the divergence would surface as odd
     * results rather than as an error. Failing here turns an invisible mismatch into
     * an immediate, diagnosable one.
     */
    private void failOnIncompleteBlueprint(Map<LlmRole, RoleConfig> resolved) {
        for (LlmRole role : LlmRole.values()) {
            RoleConfig config = resolved.get(role);
            if (config == null) {
                throw new IllegalStateException(
                        "Blueprint '" + blueprint.id() + "' declares no configuration for role "
                                + role + ", and no usable defaults. Every role must be resolvable.");
            }
        }
    }
}
