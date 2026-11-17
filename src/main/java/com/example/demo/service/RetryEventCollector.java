package com.example.demo.service;

import com.example.demo.config.LlmRole;
import com.example.demo.config.RetryCause;
import com.example.demo.model.RetryEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-request collector of failed model invocations.
 * <p>
 * {@link ResilientLlmCaller} is a stateless utility: it can produce the record
 * of a failed attempt, but has nowhere to save it for the caller to retrieve. This
 * class does that.
 */
public final class RetryEventCollector {

    private static final InheritableThreadLocal<RetryEventCollector> CONTEXT =
            new InheritableThreadLocal<>() {
                @Override
                protected RetryEventCollector childValue(RetryEventCollector parent) {
                    return parent;
                }
            };

    private final List<RetryEvent> events = new CopyOnWriteArrayList<>();

    private RetryEventCollector() {}

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
     */
    public static void record(LlmRole role, int attemptNumber, RetryCause cause, String detail) {
        RetryEventCollector collector = CONTEXT.get();
        if (collector != null) {
            collector.events.add(RetryEvent.of(role, attemptNumber, cause, detail));
        }
    }

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
