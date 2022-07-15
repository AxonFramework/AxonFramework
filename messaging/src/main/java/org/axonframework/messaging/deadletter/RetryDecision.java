package org.axonframework.messaging.deadletter;

import java.time.Instant;

public interface RetryDecision {

    boolean shouldRetry();

    Instant retryAt();

    String describe();
}
