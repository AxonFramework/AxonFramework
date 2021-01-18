package org.axonframework.spring.config.event;

import org.axonframework.messaging.MessageHandler;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.Type;

/**
 * Spring {@link ApplicationEvent} signalling all Spring beans capable of handling {@link
 * org.axonframework.queryhandling.QueryMessage}s have been registered to the {@link
 * org.axonframework.queryhandling.QueryBus} by the {@link org.axonframework.spring.config.QueryHandlerSubscriber}.
 * <p>
 * Note that this does <b>not</b> include any custom invocations of the {@link org.axonframework.queryhandling.QueryBus#subscribe(String,
 * Type, MessageHandler)} method in user code. Hence upon handling this event it may be assumed all auto configured
 * {@code QueryMessage} handlers have been subscribed.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class QueryHandlersSubscribedEvent extends ApplicationEvent {

    /**
     * Create a new {@link QueryHandlersSubscribedEvent}.
     *
     * @param source the object on which the event initially occurred
     */
    public QueryHandlersSubscribedEvent(Object source) {
        super(source);
    }
}
