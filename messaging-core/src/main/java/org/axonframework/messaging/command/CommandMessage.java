package org.axonframework.messaging.command;

import org.axonframework.messaging.Message;

public interface CommandMessage<T> extends Message<T> {

    default int priority() {
        return 0;
    }

    default int routingKey() {
        return identifier().hashCode();
    }

}
