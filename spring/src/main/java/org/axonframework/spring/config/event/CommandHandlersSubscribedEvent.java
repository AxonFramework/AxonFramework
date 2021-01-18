package org.axonframework.spring.config.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} signalling all Spring beans capable of handling {@link
 * org.axonframework.commandhandling.CommandMessage}s have been registered to the {@link
 * org.axonframework.commandhandling.CommandBus}.
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
