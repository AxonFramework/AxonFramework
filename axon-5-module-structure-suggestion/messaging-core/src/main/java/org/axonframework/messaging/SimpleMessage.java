package org.axonframework.messaging;

import java.util.UUID;

public class SimpleMessage<T> implements Message {

    private final T payload;
    private final QualifiedName name;
    private final MetaData metaData;
    private final String identifier = UUID.randomUUID().toString();

    public SimpleMessage(QualifiedName name, T payload, MetaData metaData) {
        this.payload = payload;
        this.name = name;
        this.metaData = metaData;
    }

    @Override
    public T payload() {
        return payload;
    }

    @Override
    public QualifiedName name() {
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

    @Override
    public String toString() {
        return "SimpleMessage{" +
                "payload=" + payload +
                ", name=" + name +
                '}';
    }
}
