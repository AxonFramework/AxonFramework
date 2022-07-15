package org.axonframework.messaging.deadletter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public abstract class RetryDecisions {

    private RetryDecisions() {
    }

    public static RetryDecision retryNow() {
        return new RetryAt(Instant.now());
    }

    public static RetryDecision retryIn(Duration in) {
        return new RetryAt(Instant.now().plus(in));
    }

    public static RetryDecision retryIn(long time, TimeUnit unit) {
        return new RetryAt(Instant.now().plus(Duration.ofMillis(unit.toMillis(time))));
    }

    public static RetryDecision never() {
        return new NeverRetry();
    }

    public static RetryDecision noLonger() {
        return new NoLongerRetry();
    }
}
