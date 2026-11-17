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

        // One client per configuration, not per role
        Map<RoleConfig, ChatClient> shared = new HashMap<>();

        resolved.forEach((role, config) -> {
            ChatClient client = shared.computeIfAbsent(
                    config, cfg -> factory.create(role, cfg));
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
