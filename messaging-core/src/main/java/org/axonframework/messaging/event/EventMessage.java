package org.axonframework.messaging.event;

import org.axonframework.messaging.Message;

import java.time.Clock;
import java.time.Instant;

public interface EventMessage<T> extends Message<T> {

    Clock clock = Clock.systemUTC();

    Instant timestamp();
}
