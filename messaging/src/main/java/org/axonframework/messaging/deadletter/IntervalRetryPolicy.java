package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class IntervalRetryPolicy<T extends Message<?>> implements RetryPolicy<T> {

    private final Duration in;

    public IntervalRetryPolicy() {
        this(5, ChronoUnit.SECONDS);
    }

    public IntervalRetryPolicy(int time, TemporalUnit timeUnit) {
        this(Duration.of(time, timeUnit));
    }

    public IntervalRetryPolicy(Duration in) {
        this.in = in;
    }

    @Override
    public RetryDecision decide(DeadLetter<T> deadLetter, Throwable cause) {
        return RetryDecisions.retryIn(in);
    }
}
