/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.*;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolution;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolver;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.axonframework.common.BuilderUtils.assertNonNull;
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
    private final MessageMonitor<? super QueryMessage<?, ?>> messageMonitor;
    private final DuplicateQueryHandlerResolver duplicateQueryHandlerResolver;
    private final QueryInvocationErrorHandler errorHandler;
    private final List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final QueryBusSpanFactory spanFactory;
    private final MessageNameResolver messageNameResolver;

    private final QueryUpdateEmitter queryUpdateEmitter;

    /**
     * Instantiate a {@link SimpleQueryBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleQueryBus} instance
     */
    protected SimpleQueryBus(Builder builder) {
        builder.validate();
        this.messageMonitor = builder.messageMonitor;
        this.errorHandler = builder.errorHandler;
        if (builder.transactionManager != NoTransactionManager.INSTANCE) {
            registerHandlerInterceptor(new TransactionManagingInterceptor<>(builder.transactionManager));
        }
        this.queryUpdateEmitter = builder.queryUpdateEmitter;
        this.duplicateQueryHandlerResolver = builder.duplicateQueryHandlerResolver;
        this.spanFactory = builder.spanFactory;
        this.messageNameResolver = builder.messageNameResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SimpleQueryBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to {@link NoOpMessageMonitor}, {@link TransactionManager} to
     * {@link NoTransactionManager}, {@link QueryInvocationErrorHandler} to {@link LoggingQueryInvocationErrorHandler},
     * the {@link QueryBusSpanFactory} defaults to a {@link DefaultQueryBusSpanFactory} backed by a
     * {@link NoOpSpanFactory} and {@link QueryUpdateEmitter} to {@link SimpleQueryUpdateEmitter}.
     *
     * @return a Builder to be able to create a {@link SimpleQueryBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <R> Registration subscribe(@Nonnull String queryName,
                                      @Nonnull Type responseType,
                                      @Nonnull MessageHandler<? super QueryMessage<?, R>, ? extends QueryResponseMessage<?>> handler) {
        QuerySubscription<R> querySubscription = new QuerySubscription<>(responseType, handler);
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
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(@Nonnull QueryMessage<Q, R> query) {
        Span span = spanFactory.createQuerySpan(query, false);
        return span.runSupplier(() -> doQuery(query).whenComplete((r, t) -> {
            if (t != null) {
                span.recordException(t);
            }
        }));
    }

    @Nonnull
    private <Q, R> CompletableFuture<QueryResponseMessage<R>> doQuery(
            @Nonnull QueryMessage<Q, R> query) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.getResponseType().getExpectedResponseType()),
                       () -> "Direct query does not support Flux as a return type.");
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        QueryMessage<Q, R> interceptedQuery = intercept(query);
        List<MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>>> handlers =
                getHandlersForMessage(interceptedQuery);
        CompletableFuture<QueryResponseMessage<R>> result = new CompletableFuture<>();
        try {
            ResponseType<R> responseType = interceptedQuery.getResponseType();
            if (handlers.isEmpty()) {
                throw noHandlerException(interceptedQuery);
            }
            Iterator<MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>>> handlerIterator = handlers.iterator();
            boolean invocationSuccess = false;
            while (!invocationSuccess && handlerIterator.hasNext()) {
                DefaultUnitOfWork<QueryMessage<Q, R>> uow = DefaultUnitOfWork.startAndGet(interceptedQuery);
                ResultMessage<CompletableFuture<QueryResponseMessage<R>>> resultMessage =
                        interceptAndInvoke(uow, handlerIterator.next());
                if (resultMessage.isExceptional()) {
                    if (!(resultMessage.exceptionResult() instanceof NoHandlerForQueryException)) {
                        GenericQueryResponseMessage<R> queryResponseMessage =
                                responseType.convertExceptional(resultMessage.exceptionResult())
                                            .map(exceptionalResult -> new GenericQueryResponseMessage<>(
                                                    QualifiedNameUtils.fromClassName(exceptionalResult.getClass()),
                                                    exceptionalResult
                                            ))
                                            .orElse(new GenericQueryResponseMessage<>(
                                                    QualifiedNameUtils.fromClassName(resultMessage.exceptionResult()
                                                                                                  .getClass()),
                                                    resultMessage.exceptionResult(),
                                                    responseType.responseMessagePayloadType()
                                            ));


                        result.complete(queryResponseMessage);
                        monitorCallback.reportFailure(resultMessage.exceptionResult());
                        return result;
                    }
                } else {
                    result = resultMessage.getPayload();
                    invocationSuccess = true;
                }
            }
            if (!invocationSuccess) {
                throw noSuitableHandlerException(interceptedQuery);
            }
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            result.completeExceptionally(e);
            monitorCallback.reportFailure(e);
        }
        return result;
    }

    @Override
    public <Q, R> Publisher<QueryResponseMessage<R>> streamingQuery(StreamingQueryMessage<Q, R> query) {
        Span span = spanFactory.createStreamingQuerySpan(query, false).start();
        try (SpanScope unused = span.makeCurrent()) {
            AtomicReference<Throwable> lastError = new AtomicReference<>();
            return Mono.just(intercept(query))
                       .flatMapMany(interceptedQuery -> Mono
                               .just(interceptedQuery)
                               .flatMapMany(this::getStreamingHandlersForMessage)
                               .switchIfEmpty(Flux.error(noHandlerException(interceptedQuery)))
                               .map(handler -> interceptAndInvokeStreaming(interceptedQuery, handler, span))
                               .flatMap(new CatchLastError<>(lastError))
                               .doOnEach(new ErrorIfComplete(lastError, interceptedQuery))
                               .next()
                               .doOnEach(new SuccessReporter())
                               .flatMapMany(Message::getPayload)
                       ).contextWrite(new MonitorCallbackContextWriter(messageMonitor, query))
                       .doOnTerminate(span::end);
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
    private static class CatchLastError<R> implements Function<ResultMessage<R>, Mono<ResultMessage<R>>> {

        private final AtomicReference<Throwable> lastError;

        private CatchLastError(AtomicReference<Throwable> lastError) {
            this.lastError = lastError;
        }

        @Override
        public Mono<ResultMessage<R>> apply(ResultMessage<R> resultMessage) {
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
        private final StreamingQueryMessage<?, ?> interceptedQuery;

        private ErrorIfComplete(AtomicReference<Throwable> lastError, StreamingQueryMessage<?, ?> interceptedQuery) {
            this.lastError = lastError;
            this.interceptedQuery = interceptedQuery;
        }

        @Override
        public void accept(Signal signal) {
            if (signal.isOnComplete()) {
                Throwable throwable = lastError.get();
                if (isNull(throwable)) {
                    throw noSuitableHandlerException(interceptedQuery);
                } else {
                    throw new QueryExecutionException("Error starting stream", throwable);
                }
            }
        }
    }


    /**
     * Reports result of streaming query execution to the
     * {@link org.axonframework.monitoring.MessageMonitor.MonitorCallback} (assuming that a monitor callback is attached
     * to the context).
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
     *
     * @author Milan Savic
     */
    private static class SuccessReporter implements Consumer<Signal<?>> {

        @Override
        public void accept(Signal signal) {
            MessageMonitor.MonitorCallback m = signal.getContextView()
                                                     .get(MessageMonitor.MonitorCallback.class);
            if (signal.isOnNext()) {
                m.reportSuccess();
            } else if (signal.isOnError()) {
                m.reportFailure(signal.getThrowable());
            }
        }
    }

    /**
     * Attaches {@link org.axonframework.monitoring.MessageMonitor.MonitorCallback} to the Project Reactor's
     * {@link Context}.
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
     *
     * @author Milan Savic
     */
    private static class MonitorCallbackContextWriter implements UnaryOperator<Context> {

        private final MessageMonitor<? super QueryMessage<?, ?>> messageMonitor;
        private final StreamingQueryMessage<?, ?> query;

        private MonitorCallbackContextWriter(MessageMonitor<? super QueryMessage<?, ?>> messageMonitor,
                                             StreamingQueryMessage<?, ?> query) {
            this.messageMonitor = messageMonitor;
            this.query = query;
        }

        @Override
        public Context apply(Context ctx) {
            return ctx.put(MessageMonitor.MonitorCallback.class,
                           messageMonitor.onMessageIngested(query));
        }
    }

    private NoHandlerForQueryException noHandlerException(QueryMessage<?, ?> intercepted) {
        return new NoHandlerForQueryException(format("No handler found for [%s] with response type [%s]",
                                                     intercepted.getQueryName(),
                                                     intercepted.getResponseType()));
    }

    private static NoHandlerForQueryException noSuitableHandlerException(QueryMessage<?, ?> intercepted) {
        return new NoHandlerForQueryException(format("No suitable handler was found for [%s] with response type [%s]",
                                                     intercepted.getQueryName(),
                                                     intercepted.getResponseType()));
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(@Nonnull QueryMessage<Q, R> query, long timeout,
                                                                @Nonnull TimeUnit unit) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.getResponseType().getExpectedResponseType()),
                       () -> "Scatter-Gather query does not support Flux as a return type.");
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        QueryMessage<Q, R> interceptedQuery = intercept(query);
        List<MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>>> handlers =
                getHandlersForMessage(interceptedQuery);
        if (handlers.isEmpty()) {
            monitorCallback.reportIgnored();
            return Stream.empty();
        }

        return spanFactory.createScatterGatherSpan(query, false).runSupplier(() -> {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            List<Span> spans = handlers.stream().map(handler -> {
                int handlerIndex = handlers.indexOf(handler);
                return spanFactory.createScatterGatherHandlerSpan(query, handlerIndex);
            }).collect(Collectors.toList());
            return handlers
                    .stream()
                    .map(handler -> {
                        Span span = spans.get(handlers.indexOf(handler));
                        return span.runSupplier(
                                () -> scatterGatherHandler(span, monitorCallback, interceptedQuery, deadline, handler));
                    })
                    .filter(Objects::nonNull);
        });
    }

    private <Q, R> QueryResponseMessage<R> scatterGatherHandler(
            Span span,
            MessageMonitor.MonitorCallback monitorCallback,
            QueryMessage<Q, R> interceptedQuery,
            long deadline,
            MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>> handler
    ) {
        long leftTimeout = getRemainingOfDeadline(deadline);
        ResultMessage<CompletableFuture<QueryResponseMessage<R>>> resultMessage =
                interceptAndInvoke(DefaultUnitOfWork.startAndGet(interceptedQuery),
                                   handler);
        QueryResponseMessage<R> response = null;
        if (resultMessage.isExceptional()) {
            monitorCallback.reportFailure(resultMessage.exceptionResult());
            span.recordException(resultMessage.exceptionResult());
            errorHandler.onError(resultMessage.exceptionResult(), interceptedQuery, handler);
        } else {
            try {
                response = resultMessage.getPayload().get(leftTimeout,
                                                          TimeUnit.MILLISECONDS);
                monitorCallback.reportSuccess();
            } catch (Exception e) {
                span.recordException(e);
                monitorCallback.reportFailure(e);
                errorHandler.onError(e, interceptedQuery, handler);
            }
        }
        return response;
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage<Q, I, U> query,
            int updateBufferSize
    ) {
        assertSubQueryResponseTypes(query);
        if (queryUpdateEmitter.queryUpdateHandlerRegistered(query)) {
            throw new IllegalArgumentException("There is already a subscription with the given message identifier");
        }

        Mono<QueryResponseMessage<I>> initialResult = Mono.fromFuture(() -> query(query))
                                                          .doOnError(error -> logger.error(
                                                                  "An error happened while trying to report an initial result. Query: {}",
                                                                  query, error
                                                          ));
        UpdateHandlerRegistration<U> updateHandlerRegistration =
                queryUpdateEmitter.registerUpdateHandler(query, updateBufferSize);

        return getSubscriptionQueryResult(initialResult, updateHandlerRegistration);
    }

    private <Q, I, U> void assertSubQueryResponseTypes(SubscriptionQueryMessage<Q, I, U> query) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.getResponseType().getExpectedResponseType()),
                       () -> "Subscription Query query does not support Flux as a return type.");
        Assert.isFalse(Publisher.class.isAssignableFrom(query.getUpdateResponseType().getExpectedResponseType()),
                       () -> "Subscription Query query does not support Flux as an update type.");
    }

    private <I, U> DefaultSubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> getSubscriptionQueryResult(
            Publisher<QueryResponseMessage<I>> initialResult,
            UpdateHandlerRegistration<U> updateHandlerRegistration
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

    private <Q, R> ResultMessage<CompletableFuture<QueryResponseMessage<R>>> interceptAndInvoke(
            UnitOfWork<QueryMessage<Q, R>> uow,
            MessageHandler<? super QueryMessage<?, R>, ? extends QueryResponseMessage<?>> handler
    ) {
        return uow.executeWithResult(() -> {
            ResponseType<R> responseType = uow.getMessage().getResponseType();
            Object queryResponse = new DefaultInterceptorChain<>(uow, handlerInterceptors, handler).proceedSync();
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
    private <R> QueryResponseMessage<R> asNullableResponseMessage(Class<R> declaredType, Object result) {
        if (result instanceof QueryResponseMessage) {
            //noinspection unchecked
            return (QueryResponseMessage<R>) result;
        } else if (result instanceof ResultMessage) {
            //noinspection unchecked
            ResultMessage<R> resultMessage = (ResultMessage<R>) result;
            if (resultMessage.isExceptional()) {
                Throwable cause = resultMessage.exceptionResult();
                return new GenericQueryResponseMessage<>(messageNameResolver.resolve(cause), cause,
                        resultMessage.getMetaData(),
                        declaredType);
            }
            return new GenericQueryResponseMessage<>(
                    messageNameResolver.resolve(resultMessage.getPayload()),
                    resultMessage.getPayload(),
                    resultMessage.getMetaData()
            );
        } else if (result instanceof Message) {
            //noinspection unchecked
            Message<R> message = (Message<R>) result;
            return new GenericQueryResponseMessage<>(messageNameResolver.resolve(message.getPayload()),
                    message.getPayload(),
                    message.getMetaData());
        } else {
            QualifiedName name = result == null
                    ? QualifiedNameUtils.fromDottedName("empty.query.response")
                    : messageNameResolver.resolve(result.getClass());
            //noinspection unchecked
            return new GenericQueryResponseMessage<>(name, (R) result, declaredType);
        }
    }

    private <Q, R> ResultMessage<Publisher<QueryResponseMessage<R>>> interceptAndInvokeStreaming(
            StreamingQueryMessage<Q, R> query,
            MessageHandler<? super StreamingQueryMessage<?, R>, ? extends QueryResponseMessage<?>> handler, Span span) {
        try (SpanScope unused = span.makeCurrent()) {
            DefaultUnitOfWork<StreamingQueryMessage<Q, R>> uow = DefaultUnitOfWork.startAndGet(query);
            return uow.executeWithResult(() -> {
                Object queryResponse = new DefaultInterceptorChain<>(uow, handlerInterceptors, handler).proceedSync();
                return Flux.from(query.getResponseType()
                                      .convert(queryResponse))
                           .map(this::asResponseMessage);
            });
        }
    }

    /**
     * Creates a QueryResponseMessage for the given {@code result}. If result already implements QueryResponseMessage,
     * it is returned directly. Otherwise, a new QueryResponseMessage is created with the result as payload.
     *
     * @param result The result of a Query, to be wrapped in a QueryResponseMessage
     * @param <R>    The type of response expected
     * @return a QueryResponseMessage for the given {@code result}, or the result itself, if already a
     * QueryResponseMessage.
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    private <R> QueryResponseMessage<R> asResponseMessage(Object result) {
        if (result instanceof QueryResponseMessage) {
            return (QueryResponseMessage<R>) result;
        } else if (result instanceof ResultMessage) {
            ResultMessage<R> resultMessage = (ResultMessage<R>) result;
            return new GenericQueryResponseMessage<>(
                    messageNameResolver.resolve(resultMessage.getPayload()),
                    resultMessage.getPayload(),
                    resultMessage.getMetaData()
            );
        } else if (result instanceof Message) {
            Message<R> message = (Message<R>) result;
            return new GenericQueryResponseMessage<>(messageNameResolver.resolve(message.getPayload()),
                    message.getPayload(),
                    message.getMetaData());
        } else {
            return new GenericQueryResponseMessage<>(messageNameResolver.resolve(result), (R) result);
        }
    }

    private <R> CompletableFuture<QueryResponseMessage<R>> buildCompletableFuture(ResponseType<R> responseType,
                                                                                  Object queryResponse) {
        return CompletableFuture.completedFuture(asNullableResponseMessage(
                responseType.responseMessagePayloadType(),
                responseType.convert(queryResponse)));
    }

    @SuppressWarnings("unchecked")
    private <Q, R, T extends QueryMessage<Q, R>> T intercept(T query) {
        T intercepted = query;
        for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
            intercepted = (T) interceptor.handle(intercepted);
        }
        return intercepted;
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

    /**
     * Registers an interceptor that is used to intercept Queries before they are passed to their respective handlers.
     * The interceptor is invoked separately for each handler instance (in a separate unit of work).
     *
     * @param interceptor the interceptor to invoke before passing a Query to the handler
     * @return handle to deregister the interceptor
     */
    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super QueryMessage<?, ?>> interceptor) {
        handlerInterceptors.add(interceptor);
        return () -> handlerInterceptors.remove(interceptor);
    }

    /**
     * Registers an interceptor that intercepts Queries as they are sent. Each interceptor is called once, regardless of
     * the type of query (point-to-point or scatter-gather) executed.
     *
     * @param interceptor the interceptor to invoke when sending a Query
     * @return handle to deregister the interceptor
     */
    @Override
    public @Nonnull
    Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    @SuppressWarnings("unchecked") // Suppresses 'queryHandler' cast to `MessageHandler<? super QueryMessage<?, ?>>`
    private <Q, R> List<MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>>> getHandlersForMessage(
            QueryMessage<Q, R> queryMessage) {
        ResponseType<R> responseType = queryMessage.getResponseType();
        return subscriptions.computeIfAbsent(queryMessage.getQueryName(), k -> new CopyOnWriteArrayList<>())
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
                            .map(queryHandler -> (MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>>) queryHandler)
                            .collect(Collectors.toList());
    }

    private <Q, R> Publisher<MessageHandler<? super QueryMessage<?, ?>, ? extends QueryResponseMessage<?>>> getStreamingHandlersForMessage(
            StreamingQueryMessage<Q, R> queryMessage) {
        return Flux.fromIterable(getHandlersForMessage(queryMessage));
    }

    /**
     * Builder class to instantiate a {@link SimpleQueryBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to {@link NoOpMessageMonitor}, {@link TransactionManager} to
     * {@link NoTransactionManager}, {@link QueryInvocationErrorHandler} to {@link LoggingQueryInvocationErrorHandler},
     * the {@link QueryUpdateEmitter} to {@link SimpleQueryUpdateEmitter} and the {@link QueryBusSpanFactory} defaults
     * to a {@link DefaultQueryBusSpanFactory} backed by a {@link NoOpSpanFactory}.
     */
    public static class Builder {

        private MessageMonitor<? super QueryMessage<?, ?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private TransactionManager transactionManager = NoTransactionManager.instance();
        private QueryInvocationErrorHandler errorHandler = LoggingQueryInvocationErrorHandler.builder()
                                                                                             .logger(logger)
                                                                                             .build();
        private DuplicateQueryHandlerResolver duplicateQueryHandlerResolver = DuplicateQueryHandlerResolution.logAndAccept();
        private QueryUpdateEmitter queryUpdateEmitter = SimpleQueryUpdateEmitter.builder()
                                                                                .spanFactory(DefaultQueryUpdateEmitterSpanFactory.builder().spanFactory(NoOpSpanFactory.INSTANCE).build())
                                                                                .build();
        private QueryBusSpanFactory spanFactory = DefaultQueryBusSpanFactory.builder()
                                                                            .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                            .build();
        private MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

        /**
         * Sets the {@link MessageMonitor} used to monitor query messages. Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} used to monitor query messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(@Nonnull MessageMonitor<? super QueryMessage<?, ?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the duplicate query handler resolver. This determines which registrations are added to the query bus
         * when multiple handlers are detected for the same query.
         * <p>
         * {@link DuplicateQueryHandlerResolution} contains good examples on this and will most of the time suffice. The
         * bus defaults to {@link DuplicateQueryHandlerResolution#logAndAccept()}.
         *
         * @param duplicateQueryHandlerResolver The {@link} DuplicateQueryHandlerResolver to use when multiple
         *                                      registrations are detected
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder duplicateQueryHandlerResolver(DuplicateQueryHandlerResolver duplicateQueryHandlerResolver) {
            assertNonNull(duplicateQueryHandlerResolver, "DuplicateQueryHandlerResolver may not be null");
            this.duplicateQueryHandlerResolver = duplicateQueryHandlerResolver;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage the query handling transactions. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to manage the query handling transactions
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(@Nonnull TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link QueryInvocationErrorHandler} to handle exceptions during query handler invocation. Defaults
         * to a {@link LoggingQueryInvocationErrorHandler} using this instance it's {@link Logger}.
         *
         * @param errorHandler a {@link QueryInvocationErrorHandler} to handle exceptions during query handler
         *                     invocation
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder errorHandler(@Nonnull QueryInvocationErrorHandler errorHandler) {
            assertNonNull(errorHandler, "QueryInvocationErrorHandler may not be null");
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Sets the {@link QueryUpdateEmitter} used to emits updates for the
         * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)}. Defaults to a
         * {@link SimpleQueryUpdateEmitter}.
         *
         * @param queryUpdateEmitter the {@link QueryUpdateEmitter} used to emits updates for the
         *                           {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder queryUpdateEmitter(@Nonnull QueryUpdateEmitter queryUpdateEmitter) {
            assertNonNull(queryUpdateEmitter, "QueryUpdateEmitter may not be null");
            this.queryUpdateEmitter = queryUpdateEmitter;
            return this;
        }

        /**
         * Sets the {@link QueryBusSpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link DefaultQueryBusSpanFactory} backed by a {@link NoOpSpanFactory} by default, which provides no tracing
         * capabilities.
         *
         * @param spanFactory The {@link QueryBusSpanFactory} implementation.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull QueryBusSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Sets the {@link MessageNameResolver} to be used in order to resolve QualifiedName for published Event messages.
         * If not set, a {@link ClassBasedMessageNameResolver} is used by default.
         *
         * @param messageNameResolver which provides QualifiedName for Event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageNameResolver(MessageNameResolver messageNameResolver) {
            this.messageNameResolver = messageNameResolver;
            return this;
        }

        /**
         * Initializes a {@link SimpleQueryBus} as specified through this Builder.
         *
         * @return a {@link SimpleQueryBus} as specified through this Builder
         */
        public SimpleQueryBus build() {
            return new SimpleQueryBus(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            // Method kept for overriding
        }
    }
}
