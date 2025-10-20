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
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QueueMessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of the {@code QueryBus} that dispatches queries (through
 * {@link #query(QueryMessage, ProcessingContext)} or
 * {@link #subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)}) to the
 * {@link QueryHandler QueryHandlers} subscribed to that specific query's {@link QualifiedName name} and
 * {@link QualifiedName response type} combination.
 * <p>
 * Allows fine-grained control over
 * {@link #subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries} through
 * {@link #subscribeToUpdates(SubscriptionQueryMessage, int)},
 * {@link #emitUpdate(Predicate, Supplier, ProcessingContext)},
 * {@link #completeSubscriptions(Predicate, ProcessingContext)}, and
 * {@link #completeSubscriptionsExceptionally(Predicate, Throwable, ProcessingContext)}.
 * <p>
 * Furthermore, it is in charge of invoking the {@link #subscribe(QueryHandlerName, QueryHandler) subscribed}
 * {@link QueryHandler query handlers} when a query is being dispatched.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Milan Savic
 * @since 3.1.0
 */
public class SimpleQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryBus.class);

    private static final ResourceKey<List<Runnable>> UPDATE_TASKS_KEY = ResourceKey.withLabel("update-tasks");

    private final UnitOfWorkFactory unitOfWorkFactory;
    private final ConcurrentMap<QueryHandlerName, QueryHandler> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<SubscriptionQueryMessage, QueueMessageStream<SubscriptionQueryUpdateMessage>> updateHandlers =
            new ConcurrentHashMap<>();

    /**
     * Construct a {@code SimpleQueryBus} with the given {@code unitOfWorkFactory} and {@code queryUpdateEmitter}.
     *
     * @param unitOfWorkFactory The factory constructing
     *                          {@link org.axonframework.messaging.unitofwork.UnitOfWork units of work} to dispatch and
     *                          handle queries in.
     */
    public SimpleQueryBus(@Nonnull UnitOfWorkFactory unitOfWorkFactory) {
        this.unitOfWorkFactory = Objects.requireNonNull(unitOfWorkFactory, "The UnitOfWorkFactory must be provided.");
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlerName handlerName, @Nonnull QueryHandler queryHandler) {
        logger.debug("Subscribing query handler for name [{}].", handlerName);
        QueryHandler existingHandler = subscriptions.putIfAbsent(handlerName, queryHandler);
        if (existingHandler != null && existingHandler != queryHandler) {
            throw new DuplicateQueryHandlerSubscriptionException(handlerName, existingHandler, queryHandler);
        }
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
            MessageStream<QueryResponseMessage> responseStream = handle(query, handlerFor(query)).get();
            return containsResponseOrUserException(responseStream)
                    ? responseStream
                    : MessageStream.empty().cast();
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

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        MessageStream<SubscriptionQueryUpdateMessage> updates = subscribeToUpdates(query, updateBufferSize);
        MessageStream<QueryResponseMessage> initialResult = query(query, context);

        return initialResult.concatWith(updates.cast());
    }

    @Nonnull
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull SubscriptionQueryMessage query,
                                                                            int updateBufferSize) {
        if (hasHandlerFor(query.identifier())) {
            throw new SubscriptionQueryAlreadyRegisteredException(query.identifier());
        }
        QueueMessageStream<SubscriptionQueryUpdateMessage> output = new QueueMessageStream<>(new ArrayBlockingQueue<>(
                updateBufferSize));
        QueueMessageStream<SubscriptionQueryUpdateMessage> previous = updateHandlers.put(query, output);
        if (previous != null) {
            previous.close();
        }

        return output.onClose(() -> updateHandlers.remove(query, output));
    }

    private boolean hasHandlerFor(String queryId) {
        return updateHandlers.keySet().stream().anyMatch(m -> m.identifier().equals(queryId));
    }

    @Nonnull
    private QueryHandler handlerFor(@Nonnull QueryMessage query) {
        QueryHandlerName handlerName = new QueryHandlerName(query.type(), query.responseType());
        if (!subscriptions.containsKey(handlerName)) {
            throw NoHandlerForQueryException.forBus(query);
        }
        return subscriptions.get(handlerName);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        return runAfterCommitOrImmediately(context, () -> emitUpdate(filter, updateSupplier));
    }

    private void emitUpdate(Predicate<SubscriptionQueryMessage> filter,
                            Supplier<SubscriptionQueryUpdateMessage> updateSupplier) {
        Map<SubscriptionQueryMessage, QueueMessageStream<SubscriptionQueryUpdateMessage>> matchingHandlers =
                updateHandlers.entrySet()
                              .stream()
                              .filter(entry -> filter.test(entry.getKey()))
                              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (matchingHandlers.isEmpty()) {
            return;
        }

        SubscriptionQueryUpdateMessage update = updateSupplier.get();
        matchingHandlers.forEach((query, updateHandler) -> {
            try {
                if (!updateHandler.offer(update, Context.empty())) {
                    updateHandler.completeExceptionally(new QueryExecutionException("Subscription update buffer overflow", null));
                    updateHandlers.remove(query, updateHandler);
                }
            } catch (Exception e) {
                logger.info("An error occurred while trying to emit an update to a query '{}'. " +
                                    "The subscription will be cancelled. Exception summary: {}",
                            query.type(), e.toString());
                updateHandler.completeExceptionally(e);
                updateHandlers.remove(query, updateHandler);
            }
        });
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return runAfterCommitOrImmediately(context, () -> completeSubscriptions(filter));
    }

    private void completeSubscriptions(Predicate<SubscriptionQueryMessage> filter) {
        updateHandlers.entrySet()
                      .stream()
                      .filter(entry -> filter.test(entry.getKey()))
                      .forEach(entry -> {
                          QueueMessageStream<SubscriptionQueryUpdateMessage> updateHandler = entry.getValue();
                          try {
                              updateHandler.complete();
                          } catch (Exception e) {
                              updateHandler.completeExceptionally(e);
                          }
                          updateHandlers.remove(entry.getKey(), updateHandler);
                      });
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        return runAfterCommitOrImmediately(context, () -> completeSubscriptionsExceptionally(filter, cause));
    }

    private void completeSubscriptionsExceptionally(Predicate<SubscriptionQueryMessage> filter, Throwable cause) {
        updateHandlers.entrySet()
                      .stream()
                      .filter(entry -> filter.test(entry.getKey()))
                      .forEach(entry -> emitError(entry.getValue(), cause, entry.getKey()));
    }

    @Nonnull
    private CompletableFuture<Void> runAfterCommitOrImmediately(@Nullable ProcessingContext context,
                                                                @Nonnull Runnable updateTask) {
        if (context != null) {
            context.computeResourceIfAbsent(
                           UPDATE_TASKS_KEY,
                           () -> {
                               List<Runnable> subscriptionQueryTask = new ArrayList<>();
                               context.runOnAfterCommit(c -> subscriptionQueryTask.forEach(Runnable::run));
                               return subscriptionQueryTask;
                           }
                   )
                   .add(updateTask);
        } else {
            updateTask.run();
        }
        return FutureUtils.emptyCompletedFuture();
    }

    private void emitError(QueueMessageStream<SubscriptionQueryUpdateMessage> updateHandler,
                           Throwable cause,
                           SubscriptionQueryMessage query) {
        try {
            updateHandler.completeExceptionally(cause);
        } catch (Exception e) {
            logger.error("An error happened while trying to inform an update handler about the error. Query: {}",
                         query);
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
        descriptor.describeProperty("subscriptions", subscriptions);
        descriptor.describeProperty("updateHandlers", updateHandlers);
    }
}
