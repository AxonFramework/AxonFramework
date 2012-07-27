package org.axonframework.commandhandling;

/**
 * Interceptor that allows commands to be intercepted and modified before they are dispatched by the Command Bus. This
 * interceptor provides a very early means to alter or reject Command Messages, even before any Unit of Work is
 * created.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CommandDispatchInterceptor {

    /**
     * Invoked each time a command is about to be dispatched on a Command Bus. The given <code>commandMessage</code>
     * represents the command being dispatched.
     *
     * @param commandMessage The command message intended to be dispatched on the Command Bus
     * @return the command message to dispatch on the Command Bus
     */
    CommandMessage<?> handle(CommandMessage<?> commandMessage);
}
