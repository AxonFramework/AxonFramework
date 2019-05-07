package org.axonframework.axonserver.connector;

import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.Message;

/**
 * Interface towards a mechanism that is capable of resolving the context name to which a message should be routed. This
 * is used in multi-context situations where certain messages need to be routed to another "Bounded Context" than the
 * one the application itself is registered in.
 *
 * @param <T> The type of Message to resolve the context for
 * @author Allard Buijze
 * @since 4.2
 */
public interface TargetContextResolver<T extends Message<?>> {

    /**
     * Provides the context to which a message should be routed.
     *
     * @param message The message to route
     * @return The name of the context this message should be routed to, or {@code null} if the message does not specify
     * any context
     */
    String resolveContext(T message);

    /**
     * Returns a TargetContextResolver that uses {@code this} instance to resolve a context for given message, and if
     * it returns {@code null}, returns the results of the {@code other} resolver instead.
     *
     * @param other The resolver to provide a context if this doesn't return one
     * @return a TargetContextResolver that resolves the context from either {@code this}, or the {@code other}
     */
    default TargetContextResolver<T> orElse(TargetContextResolver<? super T> other) {
        return m -> ObjectUtils.getOrDefault(resolveContext(m), () -> other.resolveContext(m));
    }
}
