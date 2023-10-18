package org.axonframework.command.messaging;

import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SimpleMessage;

public class SimpleCommandMessage<T> extends SimpleMessage<T> implements CommandMessage {

    public SimpleCommandMessage(T payload) {
        this(QualifiedName.fromClass(payload.getClass()), payload, MetaData.empty());
    }

    public SimpleCommandMessage(QualifiedName qualifiedName, T payload) {
        this(qualifiedName, payload, MetaData.empty());
    }

    public SimpleCommandMessage(QualifiedName qualifiedName, T payload, MetaData metaData) {
        super(qualifiedName, payload, metaData);
    }
}
