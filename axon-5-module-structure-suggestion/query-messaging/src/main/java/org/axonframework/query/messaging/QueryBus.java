package org.axonframework.query.messaging;

import org.axonframework.messaging.MessageDispatcher;
import org.axonframework.messaging.MessageHandlerRegistry;
import org.axonframework.messaging.MessageStream;

// TODO We're killing the QueryBus and CommandBus interfaces, in favor of a separate dispatcher and handler registry
// The implementations would still implement both interfaces; it's purely intended to:
// 1. Steer people towards the task at hand
// 2. Allow simplified applications that just handler or dispatch, and nothing more
public interface QueryBus extends
        MessageDispatcher<QueryMessage>,
        MessageHandlerRegistry<QueryMessage, MessageStream<QueryResponseMessage>, QueryHandler> {

    // Bus is done with it's ProcessingLifecycle once result is given
    MessageStream<QueryResponseMessage> dispatch(QueryMessage query);
}

/*
CommandDispatcher
CommandHandlerRegistry

EventPublisher
EventHandlerRegistry

QueryDispatcher
QueryHandlerRegistry
*/
