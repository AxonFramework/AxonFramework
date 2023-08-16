package org.axonframework.messaging;

public interface Message<P> {

    P payload();

    QualifiedName getName();

    MetaData metaData();

    String identifier();
}
