package org.axonframework.command.messaging;

import org.axonframework.messaging.Message;

public interface CommandMessage extends Message {

    default int priority() {
        return 0;
    }

    default int routingKey() {
        return identifier().hashCode();
    }
}
