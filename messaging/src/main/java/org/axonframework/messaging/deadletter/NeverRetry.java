package org.axonframework.messaging.deadletter;

import java.time.Instant;

public class NeverRetry implements RetryDecision {

    @Override
    public boolean shouldRetry() {
        return false;
    }

    @Override
    public Instant retryAt() {
        return Instant.MIN;
    }

    @Override
    public String describe() {
        return "Will never retry";
    }
}
