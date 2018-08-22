/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.ObjectUtils.getOrDefault;
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
public class SimpleQueryBus implements QueryBus, QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryBus.class);

    private static final String QUERY_UPDATE_TASKS_RESOURCE_KEY = "/update-tasks";

    private final ConcurrentMap<String, CopyOnWriteArrayList<QuerySubscription>> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<SubscriptionQueryMessage<?, ?, ?>, FluxSinkWrapper<?>> updateHandlers = new ConcurrentHashMap<>();
    private final MessageMonitor<? super QueryMessage<?, ?>> messageMonitor;
    private final MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor;
    private final QueryInvocationErrorHandler errorHandler;
    private final List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    /**
     * Initialize the query bus without monitoring on messages and a {@link LoggingQueryInvocationErrorHandler}.
     */
    public SimpleQueryBus() {
        this(NoOpMessageMonitor.INSTANCE, NoTransactionManager.instance(),
             new LoggingQueryInvocationErrorHandler(logger));
    }

    /**
     * Initialize the query bus using given {@code transactionManager} to manage transactions around query execution
     * with. No monitoring is applied to messages and a {@link LoggingQueryInvocationErrorHandler} is used
     * to log errors on handlers during a scatter-gather query.
     *
     * @param transactionManager The transaction manager to manage transactions around query execution with
     */
    public SimpleQueryBus(TransactionManager transactionManager) {
        this(NoOpMessageMonitor.INSTANCE, transactionManager, new LoggingQueryInvocationErrorHandler(logger));
    }

    /**
     * Initialize the query bus with the given {@code messageMonitor} and given {@code errorHandler}.
     *
     * @param messageMonitor     The message monitor notified for incoming messages and their result
     * @param transactionManager The transaction manager to manage transactions around query execution with
     * @param errorHandler       The error handler to invoke when query handler report an error
     */
    public SimpleQueryBus(MessageMonitor<? super QueryMessage<?, ?>> messageMonitor,
                          TransactionManager transactionManager,
                          QueryInvocationErrorHandler errorHandler) {
        this(messageMonitor, NoOpMessageMonitor.INSTANCE, transactionManager, errorHandler);
    }

    /**
     * Initialize the query bus with the given {@code messageMonitor}, {@code updateMessageMonitor}, {@code
     * transactionManager} and given {@code errorHandler}.
     *
     * @param messageMonitor       The message monitor notified for incoming messages and their result
     * @param updateMessageMonitor The message monitor notified for incoming update message in regard to subscription
     *                             queries
     * @param transactionManager   The transaction manager to manage transactions around query execution with
     * @param errorHandler         The error handler to invoke when query handler report an error
     */
    public SimpleQueryBus(MessageMonitor<? super QueryMessage<?, ?>> messageMonitor,
                          MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor,
                          TransactionManager transactionManager,
                          QueryInvocationErrorHandler errorHandler) {
        this.messageMonitor = messageMonitor != null ? messageMonitor : NoOpMessageMonitor.instance();
        this.updateMessageMonitor = updateMessageMonitor != null ? updateMessageMonitor : NoOpMessageMonitor.instance();
        this.errorHandler = getOrDefault(errorHandler, () -> new LoggingQueryInvocationErrorHandler(logger));
        if (transactionManager != null) {
            registerHandlerInterceptor(new TransactionManagingInterceptor<>(transactionManager));
        }
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
                throw new NoHandlerForQueryException(format("No handler found for %s with response type %s",
                                                            interceptedQuery.getQueryName(),
                                                            interceptedQuery.getResponseType()));
            }
            Iterator<MessageHandler<? super QueryMessage<?, ?>>> handlerIterator = handlers.iterator();
            boolean invocationSuccess = false;
            while (!invocationSuccess && handlerIterator.hasNext()) {
                try {
                    DefaultUnitOfWork<QueryMessage<Q, R>> uow = DefaultUnitOfWork.startAndGet(interceptedQuery);
                    result = interceptAndInvoke(uow, handlerIterator.next());
                    invocationSuccess = true;
                } catch (NoHandlerForQueryException e) {
                    // Ignore this Query Handler, as we may have another one which is suitable
                }
            }
            if (!invocationSuccess) {
                throw new NoHandlerForQueryException(format("No suitable handler was found for %s with response type %s",
                                                            interceptedQuery.getQueryName(),
                                                            interceptedQuery.getResponseType()));
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
                           try {
                               long leftTimeout = getRemainingOfDeadline(deadline);
                               QueryResponseMessage<R> response =
                                       interceptAndInvoke(DefaultUnitOfWork.startAndGet(interceptedQuery), handler)
                                               .get(leftTimeout, TimeUnit.MILLISECONDS);
                               monitorCallback.reportSuccess();
                               return response;
                           } catch (Exception e) {
                               monitorCallback.reportFailure(e);
                               errorHandler.onError(e, interceptedQuery, handler);
                               return null;
                           }
                       }).filter(Objects::nonNull);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query,
            SubscriptionQueryBackpressure backpressure,
            int updateBufferSize) {
        boolean alreadyExists = updateHandlers.keySet()
                                              .stream()
                                              .anyMatch(m -> m.getIdentifier().equals(query.getIdentifier()));
        if (alreadyExists) {
            throw new IllegalArgumentException("There is already a subscription with the given message identifier");
        }

        MonoWrapper<QueryResponseMessage<I>> initialResult = MonoWrapper.create(monoSink -> query(query)
                .thenAccept(monoSink::success)
                .exceptionally(t -> {
                    logger.error(format("An error happened while trying to report an initial result. Query: %s", query),
                                 t);
                    monoSink.error(t.getCause());
                    return null;
                }));

        EmitterProcessor<SubscriptionQueryUpdateMessage<U>> processor = EmitterProcessor.create(updateBufferSize);
        FluxSink<SubscriptionQueryUpdateMessage<U>> sink = processor.sink(backpressure.getOverflowStrategy());
        sink.onDispose(() -> updateHandlers.remove(query));
        FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> fluxSinkWrapper = new FluxSinkWrapper<>(sink);
        updateHandlers.put(query, fluxSinkWrapper);

        Registration registration = () -> {
            fluxSinkWrapper.complete();
            return true;
        };

        return new DefaultSubscriptionQueryResult<>(initialResult.getMono(),
                                                    processor.replay(updateBufferSize).autoConnect(),
                                                    registration);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         SubscriptionQueryUpdateMessage<U> update) {
        runOnAfterCommitOrNow(() -> doEmit(filter, update));
    }

    @SuppressWarnings("unchecked")
    private <U> void doEmit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                            SubscriptionQueryUpdateMessage<U> update) {
        updateHandlers.keySet()
                      .stream()
                      .filter(sqm -> filter.test((SubscriptionQueryMessage<?, ?, U>) sqm))
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(uh -> doEmit(query, uh, update)));
    }

    @Override
    public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        runOnAfterCommitOrNow(() -> doComplete(filter));
    }

    private void doComplete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        updateHandlers.keySet()
                      .stream()
                      .filter(filter)
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(updateHandler -> {
                                                    try {
                                                        updateHandler.complete();
                                                    } catch (Exception e) {
                                                        emitError(query, e, updateHandler);
                                                    }
                                                }));
    }

    @Override
    public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        runOnAfterCommitOrNow(() -> doCompleteExceptionally(filter, cause));
    }

    private void doCompleteExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        updateHandlers.keySet()
                      .stream()
                      .filter(filter)
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(updateHandler -> emitError(query, cause, updateHandler)));
    }

    /**
     * Either runs the provided {@link Runnable} immediately or adds it to a {@link List} as a resource to the current
     * {@link UnitOfWork} if {@link SimpleQueryBus#inStartedPhaseOfUnitOfWork} returns {@code true}. This is done to
     * ensure any emitter calls made from a message handling function are executed in the
     * {@link UnitOfWork.Phase#AFTER_COMMIT} phase.
     * <p>
     * The latter check requires the current UnitOfWork's phase to be {@link UnitOfWork.Phase#STARTED}. This is done
     * to allow users to circumvent their {@code queryUpdateTask} being handled in the AFTER_COMMIT phase. They can do
     * this by retrieving the current UnitOfWork and performing any of the {@link QueryUpdateEmitter} calls in a
     * different phase.
     *
     * @param queryUpdateTask a {@link Runnable} to be ran immediately or as a resource if {@link
     *                        SimpleQueryBus#inStartedPhaseOfUnitOfWork} returns {@code true}
     */
    private void runOnAfterCommitOrNow(Runnable queryUpdateTask) {
        if (inStartedPhaseOfUnitOfWork()) {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            unitOfWork.getOrComputeResource(
                    this.toString() + QUERY_UPDATE_TASKS_RESOURCE_KEY,
                    resourceKey -> {
                        List<Runnable> queryUpdateTasks = new ArrayList<>();
                        unitOfWork.afterCommit(uow -> queryUpdateTasks.forEach(Runnable::run));
                        return queryUpdateTasks;
                    }
            ).add(queryUpdateTask);
        } else {
            queryUpdateTask.run();
        }
    }

    /**
     * Return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link UnitOfWork.Phase#STARTED}, otherwise {@code false}.
     *
     * @return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link UnitOfWork.Phase#STARTED}, otherwise {@code false}.
     */
    private boolean inStartedPhaseOfUnitOfWork() {
        return CurrentUnitOfWork.isStarted() && UnitOfWork.Phase.STARTED.equals(CurrentUnitOfWork.get().phase());
    }

    /**
     * Provides the set of running subscription queries. If there are changes to subscriptions they will be reflected in
     * the returned set of this method.
     *
     * @return the set of running subscription queries
     */
    public Set<SubscriptionQueryMessage<?, ?, ?>> activeSubscriptions() {
        return Collections.unmodifiableSet(updateHandlers.keySet());
    }

    @SuppressWarnings("unchecked")
    private <U> void doEmit(SubscriptionQueryMessage<?, ?, ?> query, FluxSinkWrapper<?> updateHandler,
                            SubscriptionQueryUpdateMessage<U> update) {
        MessageMonitor.MonitorCallback monitorCallback = updateMessageMonitor.onMessageIngested(update);
        try {
            ((FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>>) updateHandler).next(update);
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            logger.info("An error occurred while trying to emit an update to a query '{}'. " +
                                "The subscription will be cancelled. Exception summary: {}",
                        query.getQueryName(), e.toString(), logger.isDebugEnabled() ? e : "");
            monitorCallback.reportFailure(e);
            updateHandlers.remove(query);
            emitError(query, e, updateHandler);
        }
    }

    private void emitError(SubscriptionQueryMessage<?, ?, ?> query, Throwable cause,
                           FluxSinkWrapper<?> updateHandler) {
        try {
            updateHandler.error(cause);
        } catch (Exception e) {
            logger.error(format("An error happened while trying to inform update handler about the error. Query: %s",
                                query));
        }
    }

    @SuppressWarnings("unchecked")
    private <Q, R> CompletableFuture<QueryResponseMessage<R>> interceptAndInvoke(UnitOfWork<QueryMessage<Q, R>> uow,
                                                                                 MessageHandler<? super QueryMessage<?, R>> handler)
            throws Exception {
        return uow.executeWithResult(() -> {
            ResponseType<R> responseType = uow.getMessage().getResponseType();
            Object queryResponse = new DefaultInterceptorChain<>(uow, handlerInterceptors, handler).proceed();
            if (queryResponse instanceof CompletableFuture) {
                return ((CompletableFuture) queryResponse).thenCompose(
                        result -> buildCompletableFuture(responseType, result));
            } else if (queryResponse instanceof Future) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return ((Future) queryResponse).get();
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
}
