package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.messaging.Message;

/**
 * Interface describing a message handler capable of forwarding a specific command.
 *
 * @since 4.6
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the command
 */
public interface ForwardingMessageHandlingMember<T> extends CommandMessageHandlingMember<T> {
    /**
     * Check if this handler is in a state where it can currently accept the command
     *
     * @param message The message that is to be forwarded
     * @param target  The target to forward the message
     * @return {@code true} if this handler can forward command to target entity, {@code false} otherwise.
     */
    boolean canForward(Message<?> message, T target);
}
