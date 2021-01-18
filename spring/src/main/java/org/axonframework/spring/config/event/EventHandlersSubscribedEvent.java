package org.axonframework.spring.config.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} signalling all Spring beans capable of handling {@link
 * org.axonframework.eventhandling.EventMessage}s have been registered to the {@link
 * org.axonframework.eventhandling.EventBus}.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class EventHandlersSubscribedEvent extends ApplicationEvent {

    /**
     * Create a new {@link EventHandlersSubscribedEvent}.
     *
     * @param source the object on which the event initially occurred
     */
    public EventHandlersSubscribedEvent(Object source) {
        super(source);
    }
}
