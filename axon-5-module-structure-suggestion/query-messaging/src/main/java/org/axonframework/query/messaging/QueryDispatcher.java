package org.axonframework.query.messaging;

import org.axonframework.messaging.MessageStream;

public interface QueryDispatcher {

    MessageStream<QueryResponseMessage> dispatch(QueryMessage query);
}
