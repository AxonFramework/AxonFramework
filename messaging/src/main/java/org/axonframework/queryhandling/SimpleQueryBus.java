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
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.axonframework.common.ObjectUtils.getRemainingOfDeadline;

/**
 * Implementation of the {@code QueryBus} that dispatches queries (through
 * {@link #query(QueryMessage, ProcessingContext) dispatches}, {@link #streamingQuery(StreamingQueryMessage)}, or
 * {@link #subscriptionQuery(SubscriptionQueryMessage)}) to the {@link QueryHandler QueryHandlers} subscribed to that
 * specific query's {@link QualifiedName name} and {@link ResponseType type} combination.
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

    private final QueryInvocationErrorHandler errorHandler;
    private final MessageTypeResolver messageTypeResolver;

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

        // TODO I think we can drop this, as it is only used for scatter-gather queries
        this.errorHandler = LoggingQueryInvocationErrorHandler.builder().build();
        // Replace as this is a gateway concern
        this.messageTypeResolver = new ClassBasedMessageTypeResolver();
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
    public Publisher<QueryResponseMessage> streamingQuery(StreamingQueryMessage query) {
        AtomicReference<Throwable> lastError = new AtomicReference<>();
        return Mono.just(query)
                   .flatMapMany(q -> Mono.just(q)
                                         .flatMapMany(this::getStreamingHandlersForMessage)
                                         .switchIfEmpty(Flux.error(NoHandlerForQueryException.forBus(q)))
                                         .map(handler -> invokeStreaming(q, handler))
                                         .flatMap(new CatchLastError(lastError))
                                         .doOnEach(new ErrorIfComplete(lastError, q))
                                         .next()
                                         .flatMapMany(m -> (Publisher) m.payload())
                   );
    }

    /**
     * <p>
     * The reason for this static class to exist at all is the ability of instantiating {@link SimpleQueryBus} even
     * without Project Reactor on the classpath.
     * </p>
     * <p>
     * If we had Project Reactor on the classpath, this class would be replaced with a lambda (which would compile into
     * inner class). But, inner classes have a reference to an outer class making a single unit together with it. If an
     * inner or outer class had a method with a parameter that belongs to a library which is not on the classpath,
     * instantiation would fail.
     * </p>
     */
    private static class CatchLastError implements Function<ResultMessage, Mono<ResultMessage>> {

        private final AtomicReference<Throwable> lastError;

        private CatchLastError(AtomicReference<Throwable> lastError) {
            this.lastError = lastError;
        }

        @Override
        public Mono<ResultMessage> apply(ResultMessage resultMessage) {
            if (resultMessage.isExceptional()) {
                lastError.set(resultMessage.exceptionResult());
                return Mono.empty();
            }
            return Mono.just(resultMessage);
        }
    }

    /**
     * <p>
     * The reason for this static class to exist at all is the ability of instantiating {@link SimpleQueryBus} even
     * without Project Reactor on the classpath.
     * </p>
     * <p>
     * If we had Project Reactor on the classpath, this class would be replaced with a lambda (which would compile into
     * inner class). But, inner classes have a reference to an outer class making a single unit together with it. If an
     * inner or outer class had a method with a parameter that belongs to a library which is not on the classpath,
     * instantiation would fail.
     * </p>
     */
    private static class ErrorIfComplete implements Consumer<Signal<?>> {

        private final AtomicReference<Throwable> lastError;
        private final StreamingQueryMessage q;

        private ErrorIfComplete(AtomicReference<Throwable> lastError, StreamingQueryMessage q) {
            this.lastError = lastError;
            this.q = q;
        }

        @Override
        public void accept(Signal signal) {
            if (signal.isOnComplete()) {
                Throwable throwable = lastError.get();
                if (isNull(throwable)) {
                    throw noSuitableHandlerException(q);
                } else {
                    throw new QueryExecutionException("Error starting stream", throwable);
                }
            }
        }
    }

    private static NoHandlerForQueryException noSuitableHandlerException(QueryMessage query) {
        return new NoHandlerForQueryException(format("No suitable handler was found for [%s] with response type [%s]",
                                                     query.type(),
                                                     query.responseType()));
    }

    @Override
    public Stream<QueryResponseMessage> scatterGather(@Nonnull QueryMessage query, long timeout,
                                                      @Nonnull TimeUnit unit) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.responseType().getExpectedResponseType()),
                       () -> "Scatter-Gather query does not support Flux as a return type.");
        List<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> handlers =
                getHandlersForMessage(query);
        if (handlers.isEmpty()) {
            return Stream.empty();
        }

        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        return handlers
                .stream()
                .map(handler -> scatterGatherHandler(query, deadline, handler))
                .filter(Objects::nonNull);
    }

    private QueryResponseMessage scatterGatherHandler(
            QueryMessage q,
            long deadline,
            MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> handler
    ) {
        long leftTimeout = getRemainingOfDeadline(deadline);
        ResultMessage resultMessage =
                invoke(LegacyDefaultUnitOfWork.startAndGet(q),
                       handler);
        QueryResponseMessage response = null;
        if (resultMessage.isExceptional()) {
            errorHandler.onError(resultMessage.exceptionResult(), q, handler);
        } else {
            try {
                response = ((CompletableFuture<QueryResponseMessage>) resultMessage.payload()).get(leftTimeout,
                                                                                                   TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                errorHandler.onError(e, q, handler);
            }
        }
        return response;
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

    private ResultMessage invoke(LegacyUnitOfWork<QueryMessage> uow,
                                 MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> handler) {
        return uow.executeWithResult((ctx) -> {
            ResponseType<?> responseType = uow.getMessage().responseType();
            Object queryResponse = handler.handleSync(uow.getMessage(), ctx);
            if (queryResponse instanceof CompletableFuture) {
                return ((CompletableFuture<?>) queryResponse).thenCompose(
                        result -> buildCompletableFuture(responseType, result));
            } else if (queryResponse instanceof Future) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return asNullableResponseMessage(
                                responseType.responseMessagePayloadType(),
                                responseType.convert(((Future<?>) queryResponse).get()));
                    } catch (InterruptedException | ExecutionException e) {
                        throw new QueryExecutionException("Error happened while trying to execute query handler", e);
                    }
                });
            }
            return buildCompletableFuture(responseType, queryResponse);
        });
    }

    /**
     * Creates a QueryResponseMessage for the given {@code result} with a {@code declaredType} as the result type.
     * Providing both the result type and the result allows the creation of a nullable response message, as the
     * implementation does not have to check the type itself, which could result in a
     * {@link java.lang.NullPointerException}. If result already implements QueryResponseMessage, it is returned
     * directly. Otherwise a new QueryResponseMessage is created with the declared type as the result type and the
     * result as payload.
     *
     * @param declaredType The declared type of the Query Response Message to be created.
     * @param result       The result of a Query, to be wrapped in a QueryResponseMessage
     * @param <R>          The type of response expected
     * @return a QueryResponseMessage for the given {@code result}, or the result itself, if already a
     * QueryResponseMessage.
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    private <R> QueryResponseMessage asNullableResponseMessage(Class<R> declaredType, Object result) {
        if (result instanceof QueryResponseMessage) {
            //noinspection unchecked
            return (QueryResponseMessage) result;
        } else if (result instanceof ResultMessage) {
            //noinspection unchecked
            ResultMessage resultMessage = (ResultMessage) result;
            if (resultMessage.isExceptional()) {
                Throwable cause = resultMessage.exceptionResult();
                return new GenericQueryResponseMessage(
                        messageTypeResolver.resolveOrThrow(cause),
                        cause,
                        declaredType,
                        resultMessage.metadata()
                );
            }
            return new GenericQueryResponseMessage(
                    messageTypeResolver.resolveOrThrow(resultMessage.payload()),
                    resultMessage.payload(),
                    resultMessage.metadata()
            );
        } else if (result instanceof Message) {
            //noinspection unchecked
            Message message = (Message) result;
            return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(message.payload()),
                                                   message.payload(),
                                                   message.metadata());
        } else {
            MessageType type = messageTypeResolver.resolveOrThrow(ObjectUtils.nullSafeTypeOf(result));
            //noinspection unchecked
            return new GenericQueryResponseMessage(type, (R) result, declaredType);
        }
    }

    private ResultMessage invokeStreaming(
            StreamingQueryMessage query,
            MessageHandler<? super StreamingQueryMessage, ? extends QueryResponseMessage> handler) {
        LegacyDefaultUnitOfWork<StreamingQueryMessage> uow = LegacyDefaultUnitOfWork.startAndGet(query);
        return uow.executeWithResult((ctx) -> {
            Object queryResponse = handler.handleSync(uow.getMessage(), ctx);
            return Flux.from(query.responseType().convert(queryResponse))
                       .map(this::asResponseMessage);
        });
    }

    /**
     * Creates a QueryResponseMessage for the given {@code result}. If result already implements QueryResponseMessage,
     * it is returned directly. Otherwise, a new QueryResponseMessage is created with the result as payload.
     *
     * @param result The result of a Query, to be wrapped in a QueryResponseMessage
     * @return a QueryResponseMessage for the given {@code result}, or the result itself, if already a
     * QueryResponseMessage.
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    private QueryResponseMessage asResponseMessage(Object result) {
        if (result instanceof QueryResponseMessage qrm) {
            return qrm;
        }
        if (result instanceof ResultMessage resultMessage) {
            return new GenericQueryResponseMessage(
                    messageTypeResolver.resolveOrThrow(resultMessage.payload()),
                    resultMessage.payload(),
                    resultMessage.metadata()
            );
        }
        if (result instanceof Message message) {
            return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(message.payload()),
                                                   message.payload(),
                                                   message.metadata());
        }
        return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(result), result);
    }

    private <R> CompletableFuture<QueryResponseMessage> buildCompletableFuture(ResponseType<R> responseType,
                                                                               Object queryResponse) {
        return CompletableFuture.completedFuture(asNullableResponseMessage(
                responseType.responseMessagePayloadType(),
                responseType.convert(queryResponse)));
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

    private List<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> getHandlersForMessage(
            QueryMessage queryMessage
    ) {
        return List.of();
//        ResponseType<?> responseType = queryMessage.responseType();
//        return oldsubscriptions.computeIfAbsent(queryMessage.type().name(), k -> new CopyOnWriteArrayList<>())
//                               .stream()
//                               .collect(groupingBy(
//                                       querySubscription -> responseType.matchRank(querySubscription.getResponseType()),
//                                       mapping(Function.identity(), Collectors.toList())
//                               ))
//                               .entrySet()
//                               .stream()
//                               .filter(entry -> entry.getKey() != ResponseType.NO_MATCH)
//                               .sorted((entry1, entry2) -> entry2.getKey() - entry1.getKey())
//                               .map(Map.Entry::getValue)
//                               .flatMap(Collection::stream)
//                               .map(QuerySubscription::getQueryHandler)
//                               .map(queryHandler -> (MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>) queryHandler)
//                               .collect(Collectors.toList());
    }

    private Publisher<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> getStreamingHandlersForMessage(
            StreamingQueryMessage queryMessage) {
        return Flux.fromIterable(getHandlersForMessage(queryMessage));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
        descriptor.describeProperty("queryUpdateEmitter", queryUpdateEmitter);
        descriptor.describeProperty("subscriptions", subscriptions);
    }
}
