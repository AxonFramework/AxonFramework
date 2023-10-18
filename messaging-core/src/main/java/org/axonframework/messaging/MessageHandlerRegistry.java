package org.axonframework.messaging;

import java.util.Set;

/**
 * TODO next session -> make a implementation that can be used by any Message Bus type. Or, at least try to.
 */
public interface MessageHandlerRegistry<M extends Message, R, H extends MessageHandler<M, R>> {

    /**
     * @param messageTypes These are the original types of the handler, regardless of whether an interceptor/upcaster
     *                     adjusts the qualified name of the message
     * @param handler      A {@link MessageHandler handler} or {@link MessageHandlingComponent handling component}
     *                     interested in messages dispatched with any of the given {@code messageTypes}.
     * @return A registration, allowing deregistration of the given {@code handler} with this registry.
     */
    Registration registerHandler(Set<QualifiedName> messageTypes, H handler);

    /**
     * Generic registration of a {@link MessageHandlerInterceptor} used for <b>all</b> handlers
     * {@link #registerHandler(Set, MessageHandler) registered} in this registry.
     *
     * @param interceptor
     * @return
     */
    Registration registerInterceptor(MessageHandlerInterceptor<M, R> interceptor);
}
