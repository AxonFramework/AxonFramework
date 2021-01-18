package org.axonframework.spring.config.event;

import org.springframework.context.ApplicationEvent;

import java.util.function.Function;

/**
 * Spring {@link ApplicationEvent} signalling all Spring beans capable of handling {@link
 * org.axonframework.eventhandling.EventMessage}s have been registered to {@link org.axonframework.config.EventProcessingConfigurer}
 * by the {@link org.axonframework.spring.config.EventHandlerRegistrar}.
 * <p>
 * Note that this does <b>not</b> include any custom invocations of the {@link org.axonframework.config.EventProcessingConfigurer#registerEventHandler(Function)}
 * method in user code. Hence upon handling this event it may be assumed all auto configured {@code EventMessage}
 * handlers have been subscribed.
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
