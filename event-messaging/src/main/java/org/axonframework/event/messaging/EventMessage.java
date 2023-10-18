package org.axonframework.event.messaging;

import org.axonframework.messaging.Message;

import java.time.Clock;
import java.time.Instant;

public interface EventMessage extends Message {

    Clock clock = Clock.systemUTC();

    Instant timestamp();
}
