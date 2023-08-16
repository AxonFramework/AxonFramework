package org.axonframework.messaging.command;

import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;

import java.util.UUID;

public class SimpleCommandMessage<T> implements CommandMessage<T> {

    private final T payload;
    private final QualifiedName name;
    private final MetaData metaData;
    private final String identifier = UUID.randomUUID().toString();

    public SimpleCommandMessage(T payload) {
        this(QualifiedName.fromClass(payload.getClass()), payload, MetaData.empty());
    }

    public SimpleCommandMessage(QualifiedName qualifiedName, T payload) {
        this(qualifiedName, payload, MetaData.empty());
    }

    public SimpleCommandMessage(QualifiedName qualifiedName, T payload, MetaData metaData) {
        this.name = qualifiedName;
        this.payload = payload;
        this.metaData = metaData;
    }

    public T payload() {
        return payload;
    }

    @Override
    public QualifiedName getName() {
        return name;
    }

    @Override
    public MetaData metaData() {
        return metaData;
    }

    @Override
    public String identifier() {
        return identifier;
    }

}
