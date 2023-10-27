package org.axonframework.messaging;

public interface Message {

    String identifier();

    QualifiedName name();

    Object payload();

    MetaData metaData();
}
