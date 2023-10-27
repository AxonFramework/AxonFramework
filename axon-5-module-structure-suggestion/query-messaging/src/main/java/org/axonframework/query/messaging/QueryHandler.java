package org.axonframework.query.messaging;

import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlingContext;
import org.axonframework.messaging.MessageStream;

public interface QueryHandler extends MessageHandler<QueryMessage, MessageStream<QueryResponseMessage>> {

    @Override
    MessageStream<QueryResponseMessage> handle(MessageHandlingContext<QueryMessage> handlingContext);
}
