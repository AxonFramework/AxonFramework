package org.axonframework.messaging;

public interface MessageHandlingContext<M extends Message<?>> {

    M message();

    ProcessingContext processingContext();

    Resources resources();

    // Add features to manage correlation data
    // How to get correlation data into newly created messages?
    // Idea: use a MessageFactory to create message instances. Expose MessageFactory from here.
}
