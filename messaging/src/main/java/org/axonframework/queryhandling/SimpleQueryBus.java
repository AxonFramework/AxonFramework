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
import org.axonframework.common.Assert;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.Registration;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolution;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolver;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.axonframework.common.ObjectUtils.getRemainingOfDeadline;

/**
 * Implementation of the QueryBus that dispatches queries to the handlers within the JVM. Any timeouts are ignored by
 * this implementation, as handlers are considered to answer immediately.
 * <p>
 * In case multiple handlers are registered for the same query and response type, the {@link #query(QueryMessage)}
 * method will invoke one of these handlers. Which one is unspecified.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Milan Savic
 * @since 3.1
 */
public class SimpleQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryBus.class);

    private final ConcurrentMap<String, List<QuerySubscription<?>>> subscriptions = new ConcurrentHashMap<>();

    private final UnitOfWorkFactory unitOfWorkFactory;
    private final QueryUpdateEmitter queryUpdateEmitter;

    private final QueryInvocationErrorHandler errorHandler;
    private final MessageTypeResolver messageTypeResolver;
    private final DuplicateQueryHandlerResolver duplicateQueryHandlerResolver;

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
        // Replace for in-subscribe logic
        this.duplicateQueryHandlerResolver = DuplicateQueryHandlerResolution.logAndAccept();
    }

    @Override
    public Registration subscribe(@Nonnull String queryName,
                                  @Nonnull Type responseType,
                                  @Nonnull MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> handler) {
        QuerySubscription<?> querySubscription = new QuerySubscription<>(responseType, handler);
        List<QuerySubscription<?>> handlers =
                subscriptions.computeIfAbsent(queryName, k -> new CopyOnWriteArrayList<>());
        if (handlers.contains(querySubscription)) {
            return () -> unsubscribe(queryName, querySubscription);
        }
        List<QuerySubscription<?>> existingHandlers = handlers.stream()
                                                              .filter(q -> q.getResponseType().equals(responseType))
                                                              .collect(Collectors.toList());
        if (existingHandlers.isEmpty()) {
            handlers.add(querySubscription);
        } else {
            List<QuerySubscription<?>> resolvedHandlers =
                    duplicateQueryHandlerResolver.resolve(queryName, responseType, existingHandlers, querySubscription);
            subscriptions.put(queryName, resolvedHandlers);
        }

        return () -> unsubscribe(queryName, querySubscription);
    }

    private <R> boolean unsubscribe(String queryName, QuerySubscription<R> querySubscription) {
        subscriptions.computeIfPresent(queryName, (key, handlers) -> {
            handlers.remove(querySubscription);
            if (handlers.isEmpty()) {
                return null;
            }
            return handlers;
        });
        return true;
    }

    @Override
    public CompletableFuture<QueryResponseMessage> query(@Nonnull QueryMessage query) {
        return doQuery(query);
    }

    @Nonnull
    private CompletableFuture<QueryResponseMessage> doQuery(
            @Nonnull QueryMessage query) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.responseType().getExpectedResponseType()),
                       () -> "Direct query does not support Flux as a return type.");
        List<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> handlers =
                getHandlersForMessage(query);
        CompletableFuture<QueryResponseMessage> result = new CompletableFuture<>();
        try {
            ResponseType<?> responseType = query.responseType();
            if (handlers.isEmpty()) {
                throw noHandlerException(query);
            }
            Iterator<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> handlerIterator = handlers.iterator();
            boolean invocationSuccess = false;
            while (!invocationSuccess && handlerIterator.hasNext()) {
                LegacyDefaultUnitOfWork<QueryMessage> uow = LegacyDefaultUnitOfWork.startAndGet(query);
                ResultMessage resultMessage =
                        invoke(uow, handlerIterator.next());
                if (resultMessage.isExceptional()) {
                    if (!(resultMessage.exceptionResult() instanceof NoHandlerForQueryException)) {
                        GenericQueryResponseMessage queryResponseMessage =
                                responseType.convertExceptional(resultMessage.exceptionResult())
                                            .map(exceptionalResult -> new GenericQueryResponseMessage(
                                                    messageTypeResolver.resolveOrThrow(exceptionalResult),
                                                    exceptionalResult
                                            ))
                                            .orElse(new GenericQueryResponseMessage(
                                                    messageTypeResolver.resolveOrThrow(resultMessage.exceptionResult()),
                                                    resultMessage.exceptionResult(),
                                                    responseType.responseMessagePayloadType()
                                            ));


                        result.complete(queryResponseMessage);
                        return result;
                    }
                } else {
                    result = (CompletableFuture<QueryResponseMessage>) resultMessage.payload();
                    invocationSuccess = true;
                }
            }
            if (!invocationSuccess) {
                throw noSuitableHandlerException(query);
            }
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    @Override
    public Publisher<QueryResponseMessage> streamingQuery(StreamingQueryMessage query) {
        AtomicReference<Throwable> lastError = new AtomicReference<>();
        return Mono.just(query)
                   .flatMapMany(q -> Mono.just(q)
                                         .flatMapMany(this::getStreamingHandlersForMessage)
                                         .switchIfEmpty(Flux.error(noHandlerException(q)))
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

    private NoHandlerForQueryException noHandlerException(QueryMessage query) {
        return new NoHandlerForQueryException(format("No handler found for [%s] with response type [%s]",
                                                     query.type(),
                                                     query.responseType()));
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

        Mono<QueryResponseMessage> initialResult = Mono.fromFuture(() -> query(query))
                                                       .doOnError(error -> logger.error(
                                                               "An error happened while trying to report an initial result. Query: {}",
                                                               query,
                                                               error
                                                       ));
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
                        resultMessage.metaData()
                );
            }
            return new GenericQueryResponseMessage(
                    messageTypeResolver.resolveOrThrow(resultMessage.payload()),
                    resultMessage.payload(),
                    resultMessage.metaData()
            );
        } else if (result instanceof Message) {
            //noinspection unchecked
            Message message = (Message) result;
            return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(message.payload()),
                                                   message.payload(),
                                                   message.metaData());
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
                    resultMessage.metaData()
            );
        }
        if (result instanceof Message message) {
            return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(message.payload()),
                                                   message.payload(),
                                                   message.metaData());
        }
        return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(result), result);
    }

    private <R> CompletableFuture<QueryResponseMessage> buildCompletableFuture(ResponseType<R> responseType,
                                                                               Object queryResponse) {
        return CompletableFuture.completedFuture(asNullableResponseMessage(
                responseType.responseMessagePayloadType(),
                responseType.convert(queryResponse)));
    }

    /**
     * Returns the subscriptions for this query bus. While the returned map is unmodifiable, it may or may not reflect
     * changes made to the subscriptions after the call was made.
     *
     * @return the subscriptions for this query bus
     */
    protected Map<String, Collection<QuerySubscription<?>>> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    private List<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> getHandlersForMessage(
            QueryMessage queryMessage) {
        ResponseType<?> responseType = queryMessage.responseType();
        return subscriptions.computeIfAbsent(queryMessage.type().name(), k -> new CopyOnWriteArrayList<>())
                            .stream()
                            .collect(groupingBy(
                                    querySubscription -> responseType.matchRank(querySubscription.getResponseType()),
                                    mapping(Function.identity(), Collectors.toList())
                            ))
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getKey() != ResponseType.NO_MATCH)
                            .sorted((entry1, entry2) -> entry2.getKey() - entry1.getKey())
                            .map(Map.Entry::getValue)
                            .flatMap(Collection::stream)
                            .map(QuerySubscription::getQueryHandler)
                            .map(queryHandler -> (MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>) queryHandler)
                            .collect(Collectors.toList());
    }

    private Publisher<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> getStreamingHandlersForMessage(
            StreamingQueryMessage queryMessage) {
        return Flux.fromIterable(getHandlersForMessage(queryMessage));
    }
}
