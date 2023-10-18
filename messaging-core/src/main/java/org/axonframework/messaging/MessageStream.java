package org.axonframework.messaging;

import org.reactivestreams.Publisher;

import java.util.stream.Stream;

public interface MessageStream<M extends Message> {

    /*
     * 1. Throw exception for second invocation
     */
    Stream<M> asStream();

    /*
     * 1. Throw exception for second invocation
     */
    Publisher<M> asPublisher();
}
