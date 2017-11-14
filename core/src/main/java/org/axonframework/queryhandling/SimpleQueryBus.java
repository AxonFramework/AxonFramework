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
import org.axonframework.messaging.MessageHandler;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of the QueryBus that dispatches queries to the handlers.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class SimpleQueryBus implements QueryBus {
    final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?>>>> subscriptions = new ConcurrentHashMap<>();
    private final MessageMonitor<? super QueryMessage<?>> messageMonitor;

    public SimpleQueryBus() {
        this(NoOpMessageMonitor.INSTANCE);
    }
    public SimpleQueryBus(MessageMonitor<? super QueryMessage<?>> messageMonitor) {
        this.messageMonitor = messageMonitor;
    }

    @Override
    public Registration subscribe(String queryName, String responseName, MessageHandler<? super QueryMessage<?>> handler) {
        QueryDefinition registrationKey = new QueryDefinition(queryName, responseName);
        subscriptions.computeIfAbsent(registrationKey, (k) -> new CopyOnWriteArraySet<>()).add(handler);
        return () -> unsubscribe(registrationKey, handler);
    }

    @Override
    public <Q, R> CompletableFuture<R> query(QueryMessage<Q> query) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        CompletableFuture<R> completableFuture = new CompletableFuture<>();
        Set<MessageHandler<? super QueryMessage<?>>> handlers = getHandlersForMessage(query);
        try {
            //noinspection unchecked
            completableFuture.complete((R) handlers.iterator().next().handle(query));
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            completableFuture.completeExceptionally(new QueryExecutionException(e));
            monitorCallback.reportFailure(e);
        }
        return completableFuture;
    }

    private boolean unsubscribe(QueryDefinition registrationKey, MessageHandler<? super QueryMessage<?>> handler) {
        subscriptions.computeIfPresent(registrationKey, (key, handlers) -> {
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                return null;
            }
            return handlers;
        });
        return true;
    }

    @Override
    public <Q, R> Stream<R> queryAll(QueryMessage<Q> query, long timeout, TimeUnit unit) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        Set<MessageHandler<? super QueryMessage<?>>> handlers = getHandlersForMessage(query);
        return StreamSupport.stream(new Spliterator<R>() {
            final Iterator<MessageHandler<? super QueryMessage<?>>> handlerIterator = handlers.iterator();

            @SuppressWarnings("unchecked")
            public boolean tryAdvance(Consumer<? super R> action) {
                if (handlerIterator.hasNext()) {
                    try {
                        action.accept((R) handlerIterator.next().handle(query));
                        return true;
                    } catch (Exception e) {
                        monitorCallback.reportFailure(e);
                        throw new QueryExecutionException(e);
                    }
                }
                monitorCallback.reportSuccess();
                return false;
            }

            @Override
            public Spliterator<R> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return handlers.size();
            }

            @Override
            public int characteristics() {
                return Spliterator.SIZED;
            }
        }, false);
    }

    private <Q> Set<MessageHandler<? super QueryMessage<?>>> getHandlersForMessage(QueryMessage<Q> queryMessage) {
        Set<MessageHandler<? super QueryMessage<?>>> handlers = subscriptions.get(new QueryDefinition(queryMessage.getQueryName(), queryMessage.getResponseName()));
        if (handlers == null || handlers.isEmpty()) {
            throw new NoHandlerForQueryException(MessageFormat.format("No handler found for %s with response name %s", queryMessage.getQueryName(), queryMessage.getResponseName()));
        }
        return handlers;
    }

    private class QueryDefinition {
        private final String queryName;
        private final String responseName;

        private QueryDefinition(String queryName, String responseName) {
            this.queryName = queryName;
            this.responseName = responseName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QueryDefinition that = (QueryDefinition) o;

            return queryName.equals(that.queryName)
                    && responseName.equals(that.responseName);
        }

        @Override
        public int hashCode() {
            int result = queryName.hashCode();
            result = 31 * result + responseName.hashCode();
            return result;
        }
    }
}
