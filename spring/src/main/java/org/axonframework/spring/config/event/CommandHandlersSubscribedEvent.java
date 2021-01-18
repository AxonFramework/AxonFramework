package org.axonframework.spring.config.event;

import org.axonframework.messaging.MessageHandler;
import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} signalling all Spring beans capable of handling {@link
 * org.axonframework.commandhandling.CommandMessage}s have been registered to the {@link
 * org.axonframework.commandhandling.CommandBus} by the {@link org.axonframework.spring.config.CommandHandlerSubscriber}.
 * <p>
 * Note that this does <b>not</b> include any custom invocations of the {@link org.axonframework.commandhandling.CommandBus#subscribe(String,
 * MessageHandler)} method in user code. Hence upon handling this event it may be assumed all auto configured {@code
 * CommandMessage} handlers have been subscribed.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class CommandHandlersSubscribedEvent extends ApplicationEvent {

    /**
     * Create a new {@link CommandHandlersSubscribedEvent}.
     *
     * @param source the object on which the event initially occurred
     */
    public CommandHandlersSubscribedEvent(Object source) {
        super(source);
    }
}
