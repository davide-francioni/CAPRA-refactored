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
 * <b>Strategy Changed for the benchmark.</b> The caller is now identified by its {@link LlmRole}
 * rather than by a free-text name, and the number of attempts comes from the blueprint's
 * {@link RetryPolicy} rather than from a constant. Every failed attempt is recorded with
 * its cause classified, which is what makes it acceptable to have disabled the
 * transport-level retry: an infrastructure problem stays distinguishable from the model
 * being unable to do the job.
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
     * Calls the LLM via a {@link BeanOutputConverter} with retry and lenient JSON parsing.
     *
     * @param chatClient   the client for this role
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt (format instructions are added automatically)
     * @param type         the target class for parsing
     * @param role         which role is calling; recorded with every failed attempt
     * @param policy       how many attempts to make, from the blueprint
     * @param <T>          target type
     * @return the parsed response
     * @throws RuntimeException if all attempts fail
     */
    public static <T> T callEntity(ChatClient chatClient, String systemPrompt, String userPrompt,
                                   Class<T> type, LlmRole role, RetryPolicy policy) {
        var converter = new BeanOutputConverter<>(type, LENIENT_MAPPER);
        String fullUserPrompt = userPrompt + "\n\n" + converter.getFormat();
        int maxAttempts = policy.effectiveAttempts();

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChatResponse chatResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(fullUserPrompt)
                        .call()
                        .chatResponse();

                captureTokenUsage(chatResponse, role);

                String content = (chatResponse != null && chatResponse.getResult() != null)
                        ? chatResponse.getResult().getOutput().getText()
                        : null;
                if (content == null || content.isBlank()) {
                    throw new RuntimeException("Empty or null content in LLM response");
                }
                return converter.convert(content);

            } catch (Exception e) {
                lastError = e;
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
                }
            }
        }
        throw new RuntimeException("Error in " + role + " after " + maxAttempts
                + " attempts: " + lastError.getMessage(), lastError);
    }

    /**
     * Classifies why an invocation failed.
     */
    public static RetryCause classifyCause(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            String simple = t.getClass().getSimpleName().toLowerCase();

            if (simple.contains("timeout")) {
                return RetryCause.TIMEOUT;
            }
            if (name.equals("java.net.ConnectException")
                    || name.equals("java.net.UnknownHostException")
                    || name.equals("java.net.NoRouteToHostException")
                    || simple.contains("connection")) {
                return RetryCause.CONNECTION_ERROR;
            }
            if (simple.contains("httpservererror") || simple.contains("httpclienterror")
                    || simple.contains("responseexception") || simple.contains("transientaiexception")) {
                return RetryCause.HTTP_ERROR;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return RetryCause.PARSE_ERROR;
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

            // removed the detection of the provider, is now saved as a role

            acc.add(role, metadata.getModel(), input, output);
            log.debug("{}: +{} in / +{} out tokens (model={})", role, input, output, metadata.getModel());
        } catch (Exception e) {
            log.debug("{}: failed to capture token usage — {}", role, e.getMessage());
        }
    }

    /**
     * Public because the agents record their own failures: they catch the exception
     * themselves, so that one failed role does not cost the whole report, and need the
     * same diagnostic text this class writes for the attempts it handles.
     */
    public static String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg != null && msg.length() > 150 ? msg.substring(0, 150) + "..." : msg;
    }
}
