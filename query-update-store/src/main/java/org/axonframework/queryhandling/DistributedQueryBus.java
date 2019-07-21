package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import reactor.util.concurrent.Queues;

import javax.annotation.Resource;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * DistributedQueryBus provides means to handle subscription with help of JPA.
 *
 * @see SimpleQueryBus
 */
public class DistributedQueryBus implements QueryBus {

    @Resource
    private SimpleQueryBus localSegment;

    @Resource
    private DistributedQueryUpdateEmitter distributedQueryUpdateEmitter;

    @Override
    public <R> Registration subscribe(String queryName, Type responseType, MessageHandler<? super QueryMessage<?, R>> handler) {
        // supports local projection only
        return localSegment.subscribe(queryName, responseType, handler);
    }


    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> query) {
        // supports local projection only
        return localSegment.query(query);
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit) {
        // supports local projection only
        return localSegment.scatterGather(query, timeout, unit);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query, SubscriptionQueryBackpressure backPressure, int updateBufferSize) {
        SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQueryResult =
                localSegment.subscriptionQuery(query, backPressure, updateBufferSize);

        UpdateHandlerRegistration<U> updateHandlerRegistration =
                distributedQueryUpdateEmitter
                        .registerUpdateHandler(query, SubscriptionQueryBackpressure.defaultBackpressure(), Queues.SMALL_BUFFER_SIZE);

        return new DefaultSubscriptionQueryResult<>(subscriptionQueryResult.initialResult(),
                updateHandlerRegistration.getUpdates(),
                updateHandlerRegistration.getRegistration());
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return distributedQueryUpdateEmitter;
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super QueryMessage<?, ?>> handlerInterceptor) {
        return localSegment.registerHandlerInterceptor(handlerInterceptor);
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return localSegment.registerDispatchInterceptor(dispatchInterceptor);
    }
}
