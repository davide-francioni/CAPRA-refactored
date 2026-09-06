package com.example.demo.service;

import com.example.demo.config.LlmRole;
import com.example.demo.config.RetryCause;
import com.example.demo.config.RetryPolicy;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Utility for resilient LLM calls with lenient JSON parsing and automatic retry.
 * <p>
 * Solves common LLM response issues:
 * <ul>
 *   <li>Trailing commas ({@code [{"a":1},]}) — main cause of Jackson errors</li>
 *   <li>Java-style comments in JSON</li>
 *   <li>Single quotes instead of double quotes</li>
 *   <li>Unexpected fields (ignoreUnknown)</li>
 * </ul>
 * <p>
 * Strategy: uses {@link BeanOutputConverter} with a lenient {@link ObjectMapper}
 * and retries according to the {@link RetryPolicy} declared in the blueprint.
 * <p>
 * <b>Every failed attempt is recorded with its cause classified</b> into
 * {@link RetryEventCollector}. That classification is what makes it acceptable to have
 * disabled the transport-level retry: without it, a momentary network interruption
 * would be indistinguishable from the model being unable to produce a valid response,
 * and the run would be recorded as a failure of the model.
 */
public final class ResilientLlmCaller {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmCaller.class);

    /** Lenient ObjectMapper that tolerates trailing commas, comments, and single quotes. */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .build()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ResilientLlmCaller() {
        // utility class — not instantiable
    }

    /**
     * Calls the LLM via {@code .entity(converter)} with retry and lenient JSON parsing.
     * <p>
     * The {@link BeanOutputConverter} automatically adds format instructions
     * to the prompt and parses the response with the configured lenient ObjectMapper.
     *
     * @param chatClient   the LLM client to use
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt (format instructions are added automatically)
     * @param type         the target class for parsing
     * @param role         the role making the call: typed, so that a failure can be
     *                     attributed to a component rather than to the system at large
     * @param policy       how many attempts to make; comes from the blueprint and is
     *                     therefore archived with the run instead of being a constant
     * @param <T>          target type
     * @return the parsed response
     * @throws RuntimeException if all attempts fail
     */
    public static <T> T callEntity(ChatClient chatClient, String systemPrompt, String userPrompt,
                                    Class<T> type, LlmRole role, RetryPolicy policy) {
        var converter = new BeanOutputConverter<>(type, LENIENT_MAPPER);
        // Append format instructions the same way Spring AI does internally
        String fullUserPrompt = userPrompt + "\n\n" + converter.getFormat();

        int maxAttempts = policy != null ? policy.effectiveAttempts() : 3;

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChatResponse chatResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(fullUserPrompt)
                        .call()
                        .chatResponse();

                // ── Capture token usage ──────────────────────────────────────
                captureTokenUsage(chatResponse, role);

                // ── Parse response content ───────────────────────────────────
                String content = (chatResponse != null && chatResponse.getResult() != null)
                        ? chatResponse.getResult().getOutput().getText()
                        : null;
                if (content == null || content.isBlank()) {
                    throw new RuntimeException("Empty or null content in LLM response");
                }
                return converter.convert(content);
            } catch (Exception e) {
                lastError = e;

                // Recorded on every failure, including the last: the purpose is not to
                // count retries but to know why the attempt failed, and the final
                // attempt is the one that decides the outcome of the run.
                RetryCause cause = classifyCause(e);
                RetryEventCollector.record(role, attempt, cause, rootCauseMessage(e));

                if (attempt < maxAttempts) {
                    long delay = attempt * 2000L;
                    log.warn("{}: attempt {}/{} failed [{}] ({}), retrying in {}ms...",
                            role, attempt, maxAttempts, cause, rootCauseMessage(e), delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("{}: attempt {}/{} failed [{}] ({}), giving up",
                            role, attempt, maxAttempts, cause, rootCauseMessage(e));
                }
            }
        }
        throw new RuntimeException("Error in " + role + " after " + maxAttempts
                + " attempts: " + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    /**
     * Classifies a failure as infrastructural or attributable to the model.
     * <p>
     * This is the whole point of recording these events. The first three causes say
     * something went wrong between here and the endpoint; the fourth says the model
     * answered but could not produce the structure it was asked for — which is exactly
     * the kind of failure this work sets out to observe.
     * <p>
     * The cause chain is walked by type, because the exception surfacing from the client
     * layer wraps the underlying error rather than exposing it. The message is consulted
     * only as a fallback.
     */
    private static RetryCause classifyCause(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException) {
                return RetryCause.TIMEOUT;
            }
            if (cause instanceof java.net.ConnectException
                    || cause instanceof java.net.UnknownHostException) {
                return RetryCause.CONNECTION_ERROR;
            }
            if (cause instanceof org.springframework.web.client.RestClientResponseException) {
                return RetryCause.HTTP_ERROR;
            }
            if (cause instanceof com.fasterxml.jackson.core.JacksonException) {
                return RetryCause.PARSE_ERROR;
            }
            cause = cause.getCause();
        }

        // Nothing matched by type. An empty response and a conversion failure are both
        // the model's doing, so they count as PARSE_ERROR rather than as infrastructure.
        String lower = rootCauseMessage(e) != null ? rootCauseMessage(e).toLowerCase() : "";
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return RetryCause.TIMEOUT;
        }
        if (lower.contains("connection") || lower.contains("connect")) {
            return RetryCause.CONNECTION_ERROR;
        }
        if (lower.contains("empty or null content") || lower.contains("json") || lower.contains("parse")) {
            return RetryCause.PARSE_ERROR;
        }
        return RetryCause.HTTP_ERROR;
    }

    private static void captureTokenUsage(ChatResponse chatResponse, LlmRole role) {
        if (chatResponse == null) return;
        try {
            var metadata = chatResponse.getMetadata();
            if (metadata == null) return;
            var usage = metadata.getUsage();
            if (usage == null) return;

            long input  = usage.getPromptTokens()     != null ? usage.getPromptTokens().longValue()     : 0L;
            long output = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0L;
            if (input == 0 && output == 0) return;

            TokenUsageAccumulator acc = TokenUsageAccumulator.current();
            if (acc == null) return;

            // Attribution by provider is left as it was. With one model configured per
            // role the split carries little meaning, but reworking it belongs to the
            // per-role accounting that is out of scope here.
            String model = metadata.getModel() != null ? metadata.getModel().toLowerCase() : "";
            if (model.contains("claude")) {
                acc.addAnthropicTokens(input, output);
                log.debug("{}: +{} in / +{} out Anthropic tokens (model={})",
                        role, input, output, metadata.getModel());
            } else {
                acc.addOpenAiTokens(input, output);
                log.debug("{}: +{} in / +{} out OpenAI tokens (model={})",
                        role, input, output, metadata.getModel());
            }
        } catch (Exception e) {
            log.debug("{}: failed to capture token usage — {}", role, e.getMessage());
        }
    }

    private static String rootCauseMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg != null && msg.length() > 150 ? msg.substring(0, 150) + "..." : msg;
    }
}
