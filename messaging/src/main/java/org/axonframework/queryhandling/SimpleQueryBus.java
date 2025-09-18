/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation of the {@code QueryBus} that dispatches queries (through
 * {@link #query(QueryMessage, ProcessingContext) dispatches} or {@link #subscriptionQuery(SubscriptionQueryMessage)})
 * to the {@link QueryHandler QueryHandlers} subscribed to that specific query's {@link QualifiedName name} and
 * {@link ResponseType type} combination.
 * <p>
 * Furthermore, it is in charge of invoking the {@link #subscribe(QueryHandlerName, QueryHandler) subscribed}
 * {@link QueryHandler query handlers} when a query is being dispatched.
 * <p>
 * In case multiple handlers are registered for the same query and response type, the
 * {@link #query(QueryMessage, ProcessingContext)} method will invoke one of these handlers. Which one is unspecified.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Milan Savic
 * @since 3.1.0
 */
public class SimpleQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryBus.class);

    private final UnitOfWorkFactory unitOfWorkFactory;
    private final QueryUpdateEmitter queryUpdateEmitter;
    private final ConcurrentMap<QueryHandlerName, List<QueryHandler>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Construct a {@code SimpleQueryBus} with the given {@code unitOfWorkFactory} and {@code queryUpdateEmitter}.
     *
     * @param unitOfWorkFactory  The factory constructing
     *                           {@link org.axonframework.messaging.unitofwork.UnitOfWork units of work} to dispatch and
     *                           handle queries in.
     * @param queryUpdateEmitter The query update emitter used to register update handlers for
     *                           {@link #subscriptionQuery(SubscriptionQueryMessage, int) subscription queries}.
     */
    public SimpleQueryBus(@Nonnull UnitOfWorkFactory unitOfWorkFactory,
                          @Nonnull QueryUpdateEmitter queryUpdateEmitter) {
        this.unitOfWorkFactory = Objects.requireNonNull(unitOfWorkFactory, "The UnitOfWorkFactory must be provided.");
        this.queryUpdateEmitter =
                Objects.requireNonNull(queryUpdateEmitter, "The QueryUpdateEmitter must be provided.");
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlerName handlerName, @Nonnull QueryHandler queryHandler) {
        logger.debug("Subscribing query handler for name [{}].", handlerName);
        subscriptions.compute(handlerName, (n, handlers) -> {
            if (handlers == null) {
                handlers = new CopyOnWriteArrayList<>();
            } else {
                logger.warn(
                        "A duplicate query handler was found for query [{}] and response [{}}]. "
                                + "This is only valid for scatter-gather queries. "
                                + "Other queries will only use one of these handlers.",
                        handlerName.queryName(), handlerName.responseName()
                );
            }
            handlers.add(queryHandler);
            return handlers;
        });
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query, @Nullable ProcessingContext context) {
        if (logger.isDebugEnabled()) {
            logger.debug("Dispatching direct-query for query name [{}] and response [{}].",
                         query.type().name(), query.responseType());
        }

        try {
            for (QueryHandler handler : handlersFor(query)) {
                MessageStream<QueryResponseMessage> responseStream = handle(query, handler).get();
                if (containsResponseOrUserException(responseStream)) {
                    return responseStream;
                }
            }
            return MessageStream.empty().cast();
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    @Nonnull
    private CompletableFuture<MessageStream<QueryResponseMessage>> handle(@Nonnull QueryMessage query,
                                                                          @Nonnull QueryHandler handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handling query [{} {name={},response={}}]",
                         query.identifier(), query.type(), query.responseType());
        }

        UnitOfWork unitOfWork = unitOfWorkFactory.create();
        return unitOfWork.executeWithResult(
                context -> {
                    MessageStream<QueryResponseMessage> result;
                    try {
                        result = handler.handle(query, context);
                    } catch (Exception e) {
                        result = MessageStream.failed(e);
                    }
                    return CompletableFuture.completedFuture(result);
                }
        );
    }

    /**
     * Validates whether the given {@code responseStream} is <b>not</b> completed or has an exception thrown by the
     * user's {@link QueryHandler}.
     * <p>
     * If it has not completed yet, we can assume responses will be returned, making it a valuable response. If it has
     * an exception that has been (consciously) thrown by the user, they should know about it, making it a valuable
     * response.
     *
     * @param responseStream The response stream to check whether it is not completed or had an exception.
     * @return {@code true} when the given {@code responseStream} is <b>not</b> completed or has an
     * {@link MessageStream#error() error} (consciously) thrown by the user, {@code false} otherwise.
     */
    private static boolean containsResponseOrUserException(MessageStream<QueryResponseMessage> responseStream) {
        return !responseStream.isCompleted()
                || responseStream.error()
                                 .map(e -> !(e instanceof NoHandlerForQueryException))
                                 .orElse(false);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage<Q, I, U> query,
            int updateBufferSize
    ) {
        assertSubQueryResponseTypes(query);
        if (queryUpdateEmitter.queryUpdateHandlerRegistered(query)) {
            throw new IllegalArgumentException("There is already a subscription with the given message identifier");
        }
        Mono<QueryResponseMessage> initialResult = null;
        // TODO #3488 - Fix once implementing subscription queries
//        Mono<QueryResponseMessage> initialResult = Mono.fromFuture(() -> query(query))
//                                                       .doOnError(error -> logger.error(
//                                                               "An error happened while trying to report an initial result. Query: {}",
//                                                               query,
//                                                               error
//                                                       ));
        UpdateHandlerRegistration updateHandlerRegistration =
                queryUpdateEmitter.registerUpdateHandler(query, updateBufferSize);

        return getSubscriptionQueryResult(initialResult, updateHandlerRegistration);
    }

    private <Q, I, U> void assertSubQueryResponseTypes(SubscriptionQueryMessage<Q, I, U> query) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.responseType().getExpectedResponseType()),
                       () -> "Subscription Query query does not support Flux as a return type.");
        Assert.isFalse(Publisher.class.isAssignableFrom(query.updatesResponseType().getExpectedResponseType()),
                       () -> "Subscription Query query does not support Flux as an update type.");
    }

    private DefaultSubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> getSubscriptionQueryResult(
            Publisher<QueryResponseMessage> initialResult,
            UpdateHandlerRegistration updateHandlerRegistration
    ) {
        return new DefaultSubscriptionQueryResult<>(Mono.from(initialResult),
                                                    updateHandlerRegistration.getUpdates(),
                                                    () -> {
                                                        updateHandlerRegistration.complete();
                                                        return true;
                                                    });
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return queryUpdateEmitter;
    }

    @Nonnull
    private List<QueryHandler> handlersFor(@Nonnull QueryMessage query) {
        ResponseType<?> responseType = query.responseType();
        QueryHandlerName handlerName = new QueryHandlerName(
                query.type().qualifiedName(),
                new QualifiedName(responseType.getExpectedResponseType())
        );
        List<QueryHandler> handlers = subscriptions.get(handlerName);
        if (handlers == null || handlers.isEmpty()) {
            throw NoHandlerForQueryException.forBus(query);
        }
        return handlers;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
        descriptor.describeProperty("queryUpdateEmitter", queryUpdateEmitter);
        descriptor.describeProperty("subscriptions", subscriptions);
    }
}
