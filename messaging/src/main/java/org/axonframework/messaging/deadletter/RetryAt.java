package org.axonframework.messaging.deadletter;

import java.time.Instant;

import static org.axonframework.common.BuilderUtils.assertNonNull;

public class RetryAt implements RetryDecision {

    private final Instant at;

    public RetryAt(Instant at) {
        assertNonNull(at, "Cannot retry if the timestamp is null.");
        this.at = at;
    }

    @Override
    public boolean shouldRetry() {
        return true;
    }

    @Override
    public Instant retryAt() {
        return at;
    }

    @Override
    public String describe() {
        return "Will retry at [" + at + "]";
    }
}
