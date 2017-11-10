package org.axonframework.queryhandling;

import org.axonframework.common.Registration;

/**
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryHandlerAdapter {
    Registration subscribe(QueryBus queryBus);
}
