package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageHandler;

import java.lang.reflect.Type;

/**
 * Encapsulates the identifying fields of a Query Handler when one is subscribed to the
 * {@link org.axonframework.queryhandling.QueryBus}. As such contains the response type of the query handler and the
 * complete handler itself.
 * The first is typically used by the QueryBus to select the right query handler when a query comes in.
 * The latter is used to perform the actual query.
 *
 * @param <R> the type of response this query subscription contains
 */
class QuerySubscription<R> {

    private final Type responseType;
    private final MessageHandler<? super QueryMessage<?, R>> queryHandler;

    QuerySubscription(Type responseType, MessageHandler<? super QueryMessage<?, R>> queryHandler) {
        this.responseType = responseType;
        this.queryHandler = queryHandler;
    }

    public Type getResponseType() {
        return responseType;
    }

    public boolean canHandle(ResponseType queryResponseType) {
        return queryResponseType.matches(responseType);
    }

    public MessageHandler<? super QueryMessage<?, R>> getQueryHandler() {
        return queryHandler;
    }
}
