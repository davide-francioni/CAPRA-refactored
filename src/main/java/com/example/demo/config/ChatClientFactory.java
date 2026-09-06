package com.example.demo.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds a chat client from a resolved role configuration.
 * <p>
 * This is what replaces the autoconfigured beans: instead of one model per provider,
 * decided at startup from a global configuration block, a model is constructed for
 * each configuration the blueprint declares.
 * <p>
 * The three providers collapse into two code paths. A local engine exposing the
 * OpenAI protocol is served by the same client as the real OpenAI API, differing only
 * in the base URL — which is why standardising on OpenAI-compatible engines costs no
 * new integration work.
 */
@Component
public class ChatClientFactory {

    /**
     * Creates a client for one role.
     *
     * @param config the resolved configuration, defaults already merged in
     * @return a client ready to be used by the corresponding consumer
     * @throws IllegalArgumentException if the configuration is incomplete
     */
    public ChatClient create(RoleConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("No configuration provided");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IllegalArgumentException("No model declared for configuration: " + config);
        }

        return switch (config.provider()) {
            case ANTHROPIC -> ChatClient.builder(anthropicModel(config)).build();
            case OPENAI, OPENAI_COMPATIBLE -> ChatClient.builder(openAiModel(config)).build();
            case null -> throw new IllegalArgumentException("No provider declared for: " + config);
        };
    }

    private OpenAiChatModel openAiModel(RoleConfig config) {
        // Endpoint and credentials belong to the API object, not to the options: the
        // options describe the request, the API object describes where it goes.
        OpenAiApi.Builder api = OpenAiApi.builder();

        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            // Note: the client appends the completions path itself, so the base URL must
            // stop at the host and port. A local engine served at http://host:8000/v1
            // must therefore be declared as http://host:8000.
            api.baseUrl(config.baseUrl());
        }
        // A local engine needs no authentication, but the client still requires a
        // non-null value: hence the placeholder rather than an empty string.
        api.apiKey(config.apiKey() != null ? config.apiKey() : "not-required");

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(config.model());

        Map<String, Object> extra = config.options();
        if (extra != null) {
            asDouble(extra.get("temperature")).ifPresent(options::temperature);
            asInteger(extra.get("seed")).ifPresent(options::seed);
            asInteger(extra.get("max-completion-tokens")).ifPresent(options::maxCompletionTokens);
            asInteger(extra.get("maxCompletionTokens")).ifPresent(options::maxCompletionTokens);
        }

        return OpenAiChatModel.builder()
                .openAiApi(api.build())
                .defaultOptions(options.build())
                .build();
    }

    private AnthropicChatModel anthropicModel(RoleConfig config) {
        AnthropicApi.Builder api = AnthropicApi.builder();

        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            api.baseUrl(config.baseUrl());
        }
        api.apiKey(config.apiKey() != null ? config.apiKey() : "not-required");

        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                .model(config.model());

        Map<String, Object> extra = config.options();
        if (extra != null) {
            asDouble(extra.get("temperature")).ifPresent(options::temperature);
            asInteger(extra.get("max-tokens")).ifPresent(options::maxTokens);
            asInteger(extra.get("maxTokens")).ifPresent(options::maxTokens);
        }

        return AnthropicChatModel.builder()
                .anthropicApi(api.build())
                .defaultOptions(options.build())
                .build();
    }

    // --- option parsing ----------------------------------------------------- //
    // Values arrive from YAML, so an integer may surface as Integer, Long or String
    // depending on how it was written. Coercing here keeps the blueprint forgiving
    // about formatting without letting a malformed value pass silently.

    private java.util.Optional<Double> asDouble(Object value) {
        if (value instanceof Number number) {
            return java.util.Optional.of(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return java.util.Optional.of(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Integer> asInteger(Object value) {
        if (value instanceof Number number) {
            return java.util.Optional.of(number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return java.util.Optional.of(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }
}
