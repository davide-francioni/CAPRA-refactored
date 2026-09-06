package com.example.demo.service;

import com.example.demo.config.LlmRole;
import com.example.demo.config.RetryCause;
import com.example.demo.model.RetryEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-request collector of failed model invocations.
 * <p>
 * {@link ResilientLlmCaller} is a stateless utility: it can <em>produce</em> the record
 * of a failed attempt, but has nowhere to leave it for the caller to retrieve. This
 * class is that place.
 * <p>
 * It mirrors {@link TokenUsageAccumulator}, and for the same reason: the four analysis
 * agents run on virtual threads spawned from the request thread, so a plain
 * {@code ThreadLocal} would leave each of them with its own empty collector and the
 * events would be lost. An {@link InheritableThreadLocal} whose {@code childValue}
 * returns the parent instance makes every agent append to the same list.
 * <p>
 * The backing list is copy-on-write because the agents write to it concurrently while
 * the request thread reads it at the end.
 */
public final class RetryEventCollector {

    /** Shared across child threads: {@code childValue} returns the parent instance. */
    private static final InheritableThreadLocal<RetryEventCollector> CONTEXT =
            new InheritableThreadLocal<>() {
                @Override
                protected RetryEventCollector childValue(RetryEventCollector parent) {
                    return parent; // intentional: children append to the same list
                }
            };

    private final List<RetryEvent> events = new CopyOnWriteArrayList<>();

    private RetryEventCollector() {}

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public static RetryEventCollector start() {
        RetryEventCollector collector = new RetryEventCollector();
        CONTEXT.set(collector);
        return collector;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static RetryEventCollector current() {
        return CONTEXT.get();
    }

    /**
     * Records a failed attempt against the active collector, if any.
     * <p>
     * Silently does nothing when no collector is active, so that the system behaves
     * normally outside a measured request.
     */
    public static void record(LlmRole role, int attemptNumber, RetryCause cause, String detail) {
        RetryEventCollector collector = CONTEXT.get();
        if (collector != null) {
            collector.events.add(RetryEvent.of(role, attemptNumber, cause, detail));
        }
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    public List<RetryEvent> events() {
        return List.copyOf(events);
    }

    /**
     * How many failures point at the infrastructure rather than at the model.
     * <p>
     * The distinction is what allows a run to be discarded as an infrastructure
     * problem instead of being recorded as the model's inability to do the job.
     */
    public long infrastructuralCount() {
        return events.stream().filter(RetryEvent::isInfrastructural).count();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
