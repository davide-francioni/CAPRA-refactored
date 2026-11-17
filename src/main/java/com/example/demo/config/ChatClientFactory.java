package com.example.demo.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Builds a chat client from a resolved role configuration.
 * <p>
 * This is what replaces the autoconfigured beans: instead of one model per provider,
 * decided at startup from a global configuration block, a model is constructed for
 * each configuration the blueprint declares.
 */
@Component
public class ChatClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatClientFactory.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * Creates a client for one role.
     *
     * @param role   which role this client answers for. Needed because the endpoint that
     *               serves a request is recorded per role
     * @param config the resolved configuration, defaults already merged in
     * @return a client ready to be used by the corresponding consumer
     * @throws IllegalArgumentException if the configuration is incomplete
     */
    public ChatClient create(LlmRole role, RoleConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("No configuration provided");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IllegalArgumentException("No model declared for configuration: " + config);
        }

        return switch (config.provider()) {
            case ANTHROPIC -> ChatClient.builder(anthropicModel(role, config)).build();
            case OPENAI, OPENAI_COMPATIBLE -> ChatClient.builder(openAiModel(role, config)).build();
            case null -> throw new IllegalArgumentException("No provider declared for: " + config);
        };
    }

    private OpenAiChatModel openAiModel(LlmRole role, RoleConfig config) {

        OpenAiApi.Builder api = OpenAiApi.builder()
                .restClientBuilder(httpClientFor(role, config));

        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            api.baseUrl(config.baseUrl());
        }

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

    private AnthropicChatModel anthropicModel(LlmRole role, RoleConfig config) {
        AnthropicApi.Builder api = AnthropicApi.builder()
                .restClientBuilder(httpClientFor(role, config));

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

    private RestClient.Builder httpClientFor(LlmRole role, RoleConfig config) {
        int seconds = config.timeoutSeconds() > 0
                ? config.timeoutSeconds()
                : DEFAULT_TIMEOUT_SECONDS;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(seconds));

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);

        ProviderRouting routing = ProviderRouting.from(role, config);
        if (routing != null) {
            builder.requestInterceptor(routing);
            log.info("Fissaggio attivo — {}", routing);
        } else if (config.provider() == Provider.OPENAI_COMPATIBLE) {
            // Warned only for an OpenAI-compatible endpoint
            log.warn("Nessun fissaggio per {} ({}): l'endpoint puo' essere scelto "
                            + "liberamente, e due esecuzioni possono non essere confrontabili",
                    role, config.model());
        }
        return builder;
    }

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
