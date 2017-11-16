/*
 * Copyright (c) 2010-2017. Axon Framework
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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.text.MessageFormat.format;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of the QueryBus that dispatches queries to the handlers within the JVM. Any timeouts are ignored by
 * this implementation, as handlers are considered to answer immediately.
 * <p>
 * In case multiple handlers are registered for the same query and response type, the {@link #query(QueryMessage)}
 * method will invoke one of these handlers. Which one is unspecified.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class SimpleQueryBus implements QueryBus {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryBus.class);

    private final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> subscriptions = new ConcurrentHashMap<>();
    private final MessageMonitor<? super QueryMessage<?, ?>> messageMonitor;
    private final QueryInvocationErrorHandler errorHandler;
    private final List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> handlerInterceptors = new CopyOnWriteArrayList<>();
    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    /**
     * Initialize the query bus without monitoring on messages and a {@link LoggingQueryInvocationErrorHandler}.
     */
    public SimpleQueryBus() {
        this(NoOpMessageMonitor.INSTANCE, null, null);
    }

    /**
     * Initialize the query bus using given {@code transactionManager} to manage transactions around query execution
     * with. No monitoring is applied to messages and a {@link LoggingQueryInvocationErrorHandler} is used
     * to log errors on handlers during a scatter-gather query.
     *
     * @param transactionManager The transaction manager to manage transactions around query execution with
     */
    public SimpleQueryBus(TransactionManager transactionManager) {
        this(NoOpMessageMonitor.INSTANCE, transactionManager, null);
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
        this.messageMonitor = messageMonitor;
        this.errorHandler = getOrDefault(errorHandler, () -> new LoggingQueryInvocationErrorHandler(logger));
        if (transactionManager != null) {
            registerHandlerInterceptor(new TransactionManagingInterceptor<>(transactionManager));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> Registration subscribe(String queryName, Class<R> responseType, MessageHandler<? super QueryMessage<?, R>> handler) {
        QueryDefinition registrationKey = new QueryDefinition(queryName, responseType);
        subscriptions.computeIfAbsent(registrationKey, (k) -> new CopyOnWriteArraySet<>()).add((MessageHandler<? super QueryMessage<?, ?>>) handler);
        return () -> unsubscribe(registrationKey, (MessageHandler<? super QueryMessage<?, ?>>) handler);
    }

    @Override
    public <Q, R> CompletableFuture<R> query(QueryMessage<Q, R> query) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        QueryMessage<Q, R> interceptedQuery = intercept(query);
        CompletableFuture<R> completableFuture = new CompletableFuture<>();
        Set<MessageHandler<? super QueryMessage<?, ?>>> handlers = getHandlersForMessage(interceptedQuery);
        try {
            if (handlers.isEmpty()) {
                throw new NoHandlerForQueryException(format("No handler found for %s with response name %s",
                                                            interceptedQuery.getQueryName(),
                                                            interceptedQuery.getResponseType()));
            }
            DefaultUnitOfWork<QueryMessage<Q, R>> uow = DefaultUnitOfWork.startAndGet(interceptedQuery);
            completableFuture.complete(interceptAndInvoke(uow, handlers.iterator().next()));
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            completableFuture.completeExceptionally(e);
            monitorCallback.reportFailure(e);
        }
        return completableFuture;
    }

    @Override
    public <Q, R> Stream<R> queryAll(QueryMessage<Q, R> query, long timeout, TimeUnit unit) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        QueryMessage<Q, R> interceptedQuery = intercept(query);
        Set<MessageHandler<? super QueryMessage<?, ?>>> handlers = getHandlersForMessage(interceptedQuery);
        if (handlers.isEmpty()) {
            monitorCallback.reportIgnored();
            return Stream.empty();
        }

        return StreamSupport.stream(new Spliterators.AbstractSpliterator<R>(handlers.size(), Spliterator.SIZED) {
            final Iterator<MessageHandler<? super QueryMessage<?, ?>>> handlerIterator = handlers.iterator();

            @SuppressWarnings("unchecked")
            public boolean tryAdvance(Consumer<? super R> action) {
                while (handlerIterator.hasNext()) {
                    MessageHandler<? super QueryMessage<?, ?>> handler = handlerIterator.next();
                    try {
                        action.accept(interceptAndInvoke(DefaultUnitOfWork.startAndGet(interceptedQuery), handler));
                        monitorCallback.reportSuccess();
                        return true;
                    } catch (Exception e) {
                        monitorCallback.reportFailure(e);
                        errorHandler.onError(e, interceptedQuery, handler);
                    }
                }
                return false;
            }
        }, false);
    }

    @SuppressWarnings("unchecked")
    private <Q, R> R interceptAndInvoke(UnitOfWork<QueryMessage<Q, R>> uow, MessageHandler<? super QueryMessage<?, ?>> handler) throws Exception {
        return uow.executeWithResult(() -> (R) new DefaultInterceptorChain<>(uow, handlerInterceptors, handler).proceed());
    }

    @SuppressWarnings("unchecked")
    private <Q, R> QueryMessage<Q, R> intercept(QueryMessage<Q, R> query) {
        QueryMessage<Q, R> intercepted = query;
        for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
            intercepted = (QueryMessage<Q, R>) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    private boolean unsubscribe(QueryDefinition registrationKey, MessageHandler<? super QueryMessage<?, ?>> handler) {
        subscriptions.computeIfPresent(registrationKey, (key, handlers) -> {
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                return null;
            }
            return handlers;
        });
        return true;
    }

    /**
     * Returns the subscriptions for this query bus. While the returned map is unmodifiable, it may or may not reflect
     * changes made to the subscriptions after the call was made.
     *
     * @return the subscriptions for this query bus
     */
    protected Map<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    /**
     * Registers an interceptor that is used to intercept Queries before they are passed to their
     * respective handlers. The interceptor is invoked separately for each handler instance (in a separate unit of work).
     *
     * @param interceptor the interceptor to invoke before passing a Query to the handler
     */
    public void registerHandlerInterceptor(MessageHandlerInterceptor<QueryMessage<?, ?>> interceptor) {
        this.handlerInterceptors.add(interceptor);
    }

    /**
     * Registers an interceptor that intercepts Queries as they are sent. Each interceptor is called
     * once, regardless of the type of query (point-to-point or scatter-gather) executed.
     *
     * @param interceptor the interceptor to invoke when sending a Query
     */
    public void registerDispatchInterceptor(MessageDispatchInterceptor<QueryMessage<?, ?>> interceptor) {
        this.dispatchInterceptors.add(interceptor);
    }

    private <Q, R> Set<MessageHandler<? super QueryMessage<?, ?>>> getHandlersForMessage(QueryMessage<Q, R> queryMessage) {
        return subscriptions.getOrDefault(new QueryDefinition(queryMessage.getQueryName(), queryMessage.getResponseType()),
                                          Collections.emptySet());
    }

    private static class QueryDefinition {
        private final String queryName;
        private final Class<?> responseType;

        private QueryDefinition(String queryName, Class<?> responseType) {
            this.queryName = queryName;
            this.responseType = responseType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof QueryDefinition)) {
                return false;
            }
            QueryDefinition that = (QueryDefinition) o;
            return Objects.equals(queryName, that.queryName) &&
                    Objects.equals(responseType, that.responseType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queryName, responseType);
        }
    }
}
