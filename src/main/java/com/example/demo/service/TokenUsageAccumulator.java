package com.example.demo.service;

import com.example.demo.config.LlmRole;
import com.example.demo.model.TokenUsage;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-request token-usage accumulator backed by an {@link InheritableThreadLocal}.
 *
 * <p>Virtual threads spawned from the request thread inherit the same accumulator
 * reference, so counts aggregate into the same {@link AtomicLong} objects without
 * any locking overhead.
 *
 * <p><b>Counts are kept per role</b>, not only per provider. The provider split was enough
 * while the system talked to two commercial APIs and the question was «how much did this
 * report cost». Serving the models through an aggregator makes every role look like the
 * same provider.
 */
public final class TokenUsageAccumulator {

    /** Shared across child threads: {@code childValue} returns the parent instance. */
    private static final InheritableThreadLocal<TokenUsageAccumulator> CONTEXT =
            new InheritableThreadLocal<>() {
                @Override
                protected TokenUsageAccumulator childValue(TokenUsageAccumulator parent) {
                    return parent; // intentional: children accumulate into the same counters
                }
            };

    private final Map<LlmRole, AtomicLong> inputByRole = new EnumMap<>(LlmRole.class);
    private final Map<LlmRole, AtomicLong> outputByRole = new EnumMap<>(LlmRole.class);

    private final AtomicLong openAiInputTokens  = new AtomicLong(0);
    private final AtomicLong openAiOutputTokens = new AtomicLong(0);
    private final AtomicLong anthropicInputTokens  = new AtomicLong(0);
    private final AtomicLong anthropicOutputTokens = new AtomicLong(0);

    private TokenUsageAccumulator() {
        for (LlmRole role : LlmRole.values()) {
            inputByRole.put(role, new AtomicLong(0));
            outputByRole.put(role, new AtomicLong(0));
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public static TokenUsageAccumulator start() {
        TokenUsageAccumulator acc = new TokenUsageAccumulator();
        CONTEXT.set(acc);
        return acc;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static TokenUsageAccumulator current() {
        return CONTEXT.get();
    }

    // ── Accumulation ─────────────────────────────────────────────────────────

    /**
     * Records one invocation's usage.
     */
    public void add(LlmRole role, String model, long input, long output) {
        inputByRole.get(role).addAndGet(input);
        outputByRole.get(role).addAndGet(output);

        if (model != null && model.toLowerCase().contains("claude")) {
            anthropicInputTokens.addAndGet(input);
            anthropicOutputTokens.addAndGet(output);
        } else {
            openAiInputTokens.addAndGet(input);
            openAiOutputTokens.addAndGet(output);
        }
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    public long inputFor(LlmRole role)  { return inputByRole.get(role).get(); }
    public long outputFor(LlmRole role) { return outputByRole.get(role).get(); }

    /** Totals across every role — what the cost of one run is computed from */
    public long totalInputTokens() {
        return inputByRole.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public long totalOutputTokens() {
        return outputByRole.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public long getOpenAiInputTokens()     { return openAiInputTokens.get(); }
    public long getOpenAiOutputTokens()    { return openAiOutputTokens.get(); }
    public long getOpenAiTotalTokens()     { return openAiInputTokens.get() + openAiOutputTokens.get(); }

    public long getAnthropicInputTokens()  { return anthropicInputTokens.get(); }
    public long getAnthropicOutputTokens() { return anthropicOutputTokens.get(); }
    public long getAnthropicTotalTokens()  { return anthropicInputTokens.get() + anthropicOutputTokens.get(); }

    /** An immutable snapshot, suitable for returning in a response */
    public TokenUsage snapshot() {
        Map<LlmRole, TokenUsage.RoleTokens> byRole = new EnumMap<>(LlmRole.class);
        for (LlmRole role : LlmRole.values()) {
            long in = inputFor(role);
            long out = outputFor(role);
            if (in > 0 || out > 0) {
                byRole.put(role, new TokenUsage.RoleTokens(in, out));
            }
        }
        return new TokenUsage(totalInputTokens(), totalOutputTokens(), byRole);
    }
}
