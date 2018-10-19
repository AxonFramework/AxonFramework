/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
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

    private final ConcurrentMap<String, CopyOnWriteArrayList<QuerySubscription>> subscriptions = new ConcurrentHashMap<>();
    private final MessageMonitor<? super QueryMessage<?, ?>> messageMonitor;
    private final QueryInvocationErrorHandler errorHandler;
    private final List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

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
    }

    /**
     * Instantiate a Builder to be able to create a {@link SimpleQueryBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to {@link NoOpMessageMonitor}, {@link TransactionManager} to {@link
     * NoTransactionManager}, {@link QueryInvocationErrorHandler} to {@link LoggingQueryInvocationErrorHandler}, and
     * {@link QueryUpdateEmitter} to {@link SimpleQueryUpdateEmitter}.
     *
     * @return a Builder to be able to create a {@link SimpleQueryBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <R> Registration subscribe(String queryName,
                                      Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        CopyOnWriteArrayList<QuerySubscription> handlers =
                subscriptions.computeIfAbsent(queryName, k -> new CopyOnWriteArrayList<>());
        QuerySubscription<R> querySubscription = new QuerySubscription<>(responseType, handler);
        handlers.addIfAbsent(querySubscription);

        return () -> unsubscribe(queryName, querySubscription);
    }

    private boolean unsubscribe(String queryName,
                                QuerySubscription querySubscription) {
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
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> query) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        QueryMessage<Q, R> interceptedQuery = intercept(query);
        List<MessageHandler<? super QueryMessage<?, ?>>> handlers = getHandlersForMessage(interceptedQuery);
        CompletableFuture<QueryResponseMessage<R>> result = new CompletableFuture<>();
        try {
            if (handlers.isEmpty()) {
                throw new NoHandlerForQueryException(
                        format("No handler found for [%s] with response type [%s]",
                               interceptedQuery.getQueryName(),
                               interceptedQuery.getResponseType())
                );
            }
            Iterator<MessageHandler<? super QueryMessage<?, ?>>> handlerIterator = handlers.iterator();
            boolean invocationSuccess = false;
            while (!invocationSuccess && handlerIterator.hasNext()) {
                DefaultUnitOfWork<QueryMessage<Q, R>> uow = DefaultUnitOfWork.startAndGet(interceptedQuery);
                ResultMessage<CompletableFuture<QueryResponseMessage<R>>> resultMessage =
                        interceptAndInvoke(uow, handlerIterator.next());
                if (resultMessage.isExceptional()) {
                    if (!(resultMessage.exceptionResult() instanceof NoHandlerForQueryException)) {
                        result.complete(new GenericQueryResponseMessage<>(
                                interceptedQuery.getResponseType().responseMessagePayloadType(),
                                resultMessage.exceptionResult()));
                        monitorCallback.reportFailure(resultMessage.exceptionResult());
                        return result;
                    }
                } else {
                    result = resultMessage.getPayload();
                    invocationSuccess = true;
                }
            }
            if (!invocationSuccess) {
                throw new NoHandlerForQueryException(
                        format("No suitable handler was found for [%s] with response type [%s]",
                               interceptedQuery.getQueryName(),
                               interceptedQuery.getResponseType())
                );
            }
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            result.completeExceptionally(e);
            monitorCallback.reportFailure(e);
        }
        return result;
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        QueryMessage<Q, R> interceptedQuery = intercept(query);
        List<MessageHandler<? super QueryMessage<?, ?>>> handlers = getHandlersForMessage(interceptedQuery);
        if (handlers.isEmpty()) {
            monitorCallback.reportIgnored();
            return Stream.empty();
        }

        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        return handlers.stream()
                       .map(handler -> {
                           long leftTimeout = getRemainingOfDeadline(deadline);
                           ResultMessage<CompletableFuture<QueryResponseMessage<R>>> resultMessage =
                                   interceptAndInvoke(DefaultUnitOfWork.startAndGet(interceptedQuery),
                                                      handler);
                           QueryResponseMessage<R> response = null;
                           if (resultMessage.isExceptional()) {
                               monitorCallback.reportFailure(resultMessage.exceptionResult());
                               errorHandler.onError(resultMessage.exceptionResult(), interceptedQuery, handler);
                           } else {
                               try {
                                   response = resultMessage.getPayload().get(leftTimeout, TimeUnit.MILLISECONDS);
                                   monitorCallback.reportSuccess();
                               } catch (Exception e) {
                                   monitorCallback.reportFailure(e);
                                   errorHandler.onError(e, interceptedQuery, handler);
                               }
                           }
                           return response;
                       }).filter(Objects::nonNull);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query,
            SubscriptionQueryBackpressure backpressure,
            int updateBufferSize) {

        if (queryUpdateEmitter.queryUpdateHandlerRegistered(query)) {
            throw new IllegalArgumentException("There is already a subscription with the given message identifier");
        }

        MonoWrapper<QueryResponseMessage<I>> initialResult = MonoWrapper.create(monoSink -> query(query)
                .thenAccept(queryResponseMessage -> {
                    if (queryResponseMessage.isExceptional()) {
                        Throwable exceptionResult = queryResponseMessage.exceptionResult();
                        logger.error(format("An error happened while trying to report an initial result. Query: %s",
                                            query),
                                     exceptionResult);
                        monoSink.error(exceptionResult);
                    } else {
                        monoSink.success(queryResponseMessage);
                    }
                })
                .exceptionally(t -> {
                    logger.error(format("An error happened while trying to report an initial result. Query: %s", query),
                                 t);
                    monoSink.error(t.getCause());
                    return null;
                }));

        UpdateHandlerRegistration<U> updateHandlerRegistration = queryUpdateEmitter
                .registerUpdateHandler(query, backpressure, updateBufferSize);

        return new DefaultSubscriptionQueryResult<>(initialResult.getMono(),
                                                    updateHandlerRegistration.getUpdates(),
                                                    updateHandlerRegistration.getRegistration());
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return queryUpdateEmitter;
    }

    @SuppressWarnings("unchecked")
    private <Q, R> ResultMessage<CompletableFuture<QueryResponseMessage<R>>> interceptAndInvoke(
            UnitOfWork<QueryMessage<Q, R>> uow,
            MessageHandler<? super QueryMessage<?, R>> handler) {
        return uow.executeWithResult(() -> {
            ResponseType<R> responseType = uow.getMessage().getResponseType();
            Object queryResponse = new DefaultInterceptorChain<>(uow, handlerInterceptors, handler).proceed();
            if (queryResponse instanceof CompletableFuture) {
                return ((CompletableFuture) queryResponse).thenCompose(
                        result -> buildCompletableFuture(responseType, result));
            } else if (queryResponse instanceof Future) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return ((Future<QueryResponseMessage<R>>) queryResponse).get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new QueryExecutionException("Error happened while trying to execute query handler", e);
                    }
                });
            }
            return buildCompletableFuture(responseType, queryResponse);
        });
    }

    private <R> CompletableFuture<QueryResponseMessage<R>> buildCompletableFuture(ResponseType<R> responseType,
                                                                                  Object queryResponse) {
        return CompletableFuture.completedFuture(GenericQueryResponseMessage.asNullableResponseMessage(
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
    protected Map<String, Collection<QuerySubscription>> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    /**
     * Registers an interceptor that is used to intercept Queries before they are passed to their
     * respective handlers. The interceptor is invoked separately for each handler instance (in a separate unit of
     * work).
     *
     * @param interceptor the interceptor to invoke before passing a Query to the handler
     * @return handle to unregister the interceptor
     */
    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super QueryMessage<?, ?>> interceptor) {
        handlerInterceptors.add(interceptor);
        return () -> handlerInterceptors.remove(interceptor);
    }

    /**
     * Registers an interceptor that intercepts Queries as they are sent. Each interceptor is called
     * once, regardless of the type of query (point-to-point or scatter-gather) executed.
     *
     * @param interceptor the interceptor to invoke when sending a Query
     * @return handle to unregister the interceptor
     */
    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    @SuppressWarnings("unchecked") // Suppresses 'queryHandler' cast to `MessageHandler<? super QueryMessage<?, ?>>`
    private <Q, R> List<MessageHandler<? super QueryMessage<?, ?>>> getHandlersForMessage(
            QueryMessage<Q, R> queryMessage) {
        ResponseType<R> responseType = queryMessage.getResponseType();
        return subscriptions.computeIfAbsent(queryMessage.getQueryName(), k -> new CopyOnWriteArrayList<>())
                            .stream()
                            .filter(querySubscription -> responseType.matches(querySubscription.getResponseType()))
                            .map((Function<QuerySubscription, MessageHandler>) QuerySubscription::getQueryHandler)
                            .map(queryHandler -> (MessageHandler<? super QueryMessage<?, ?>>) queryHandler)
                            .collect(Collectors.toList());
    }

    /**
     * Builder class to instantiate a {@link SimpleQueryBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to {@link NoOpMessageMonitor}, {@link TransactionManager} to {@link
     * NoTransactionManager}, {@link QueryInvocationErrorHandler} to {@link LoggingQueryInvocationErrorHandler}, and
     * {@link QueryUpdateEmitter} to {@link SimpleQueryUpdateEmitter}.
     */
    public static class Builder {

        private MessageMonitor<? super QueryMessage<?, ?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private TransactionManager transactionManager = NoTransactionManager.instance();
        private QueryInvocationErrorHandler errorHandler = LoggingQueryInvocationErrorHandler.builder()
                                                                                             .logger(logger)
                                                                                             .build();
        private QueryUpdateEmitter queryUpdateEmitter = SimpleQueryUpdateEmitter.builder().build();

        /**
         * Sets the {@link MessageMonitor} used to monitor query messages. Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} used to monitor query messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(MessageMonitor<? super QueryMessage<?, ?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage the query handling transactions. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to manage the query handling transactions
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
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
        public Builder errorHandler(QueryInvocationErrorHandler errorHandler) {
            assertNonNull(errorHandler, "QueryInvocationErrorHandler may not be null");
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Sets the {@link QueryUpdateEmitter} used to emits updates for the
         * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)}. Defaults to a {@link SimpleQueryUpdateEmitter}.
         *
         * @param queryUpdateEmitter the {@link QueryUpdateEmitter} used to emits updates for the
         *                           {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder queryUpdateEmitter(QueryUpdateEmitter queryUpdateEmitter) {
            assertNonNull(queryUpdateEmitter, "QueryUpdateEmitter may not be null");
            this.queryUpdateEmitter = queryUpdateEmitter;
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
            // Kept to be overridden
        }
    }
}
