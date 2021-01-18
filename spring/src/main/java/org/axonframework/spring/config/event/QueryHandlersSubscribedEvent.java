package org.axonframework.spring.config.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} signalling all Spring beans capable of handling {@link
 * org.axonframework.queryhandling.QueryMessage}s have been registered to the {@link
 * org.axonframework.queryhandling.QueryBus}.
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
