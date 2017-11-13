package org.axonframework.queryhandling;

import org.axonframework.common.Registration;

/**
 * Describes a class capable of subscribing to the query bus.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryHandlerAdapter {
    /**
     * Subscribes query handlers to the given query bus
     * @param queryBus the query bus
     * @return a handle to unsubscribe
     */
    Registration subscribe(QueryBus queryBus);
}
