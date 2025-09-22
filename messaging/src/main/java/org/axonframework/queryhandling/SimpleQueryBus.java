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
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * Implementation of the {@code QueryBus} that dispatches queries (through
 * {@link #query(QueryMessage, ProcessingContext)} or {@link #subscriptionQuery(SubscriptionQueryMessage)})
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

    private static final ResourceKey<List<Runnable>> UPDATE_TASKS_KEY = ResourceKey.withLabel("update-tasks");

    private final UnitOfWorkFactory unitOfWorkFactory;
    private final ConcurrentMap<QueryHandlerName, QueryHandler> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<SubscriptionQueryMessage, SinkWrapper<SubscriptionQueryUpdateMessage>> updateHandlers =
            new ConcurrentHashMap<>();

    /**
     * Construct a {@code SimpleQueryBus} with the given {@code unitOfWorkFactory} and {@code queryUpdateEmitter}.
     *
     * @param unitOfWorkFactory  The factory constructing
     *                           {@link org.axonframework.messaging.unitofwork.UnitOfWork units of work} to dispatch and
     *                           handle queries in.
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
    public SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage query,
            @Nullable ProcessingContext context,
            int updateBufferSize
    ) {
        assertSubQueryResponseTypes(query);
        MessageStream<QueryResponseMessage> initialStream = query(query, context);


        // TODO #3488 - Fix once implementing subscription queries
//        Mono<QueryResponseMessage> initialResult = Mono.fromFuture(() -> query(query))
//                                                       .doOnError(error -> logger.error(
//                                                               "An error happened while trying to report an initial result. Query: {}",
//                                                               query,
//                                                               error
//                                                       ));
        UpdateHandler updateHandler = subscribe(query, updateBufferSize);
        return new DefaultSubscriptionQueryResult<>(initialStream,
                                                    updateHandler.updates(),
                                                    () -> {
                                                        updateHandler.complete();
                                                        return true;
                                                    });
    }

    @Nonnull
    public UpdateHandler subscribe(@Nonnull SubscriptionQueryMessage query,
                                    int updateBufferSize) {
        if (hasHandlerFor(query.identifier())) {
            throw new SubscriptionQueryAlreadyRegisteredException(query.identifier());
        }

        Sinks.Many<SubscriptionQueryUpdateMessage> sink = Sinks.many()
                                                               .replay()
                                                               .limit(updateBufferSize);
        SinksManyWrapper<SubscriptionQueryUpdateMessage> sinksManyWrapper = new SinksManyWrapper<>(sink);

        Runnable removeHandler = () -> updateHandlers.remove(query);

        updateHandlers.put(query, sinksManyWrapper);
        Flux<SubscriptionQueryUpdateMessage> updateMessageFlux = sink.asFlux()
                                                                     .doOnCancel(removeHandler)
                                                                     .doOnTerminate(removeHandler);
        return new UpdateHandler(updateMessageFlux, removeHandler, sinksManyWrapper::complete);
    }

    private boolean hasHandlerFor(String queryId) {
        return updateHandlers.keySet().stream().anyMatch(m -> m.identifier().equals(queryId));
    }


    private void assertSubQueryResponseTypes(SubscriptionQueryMessage query) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.responseType().getExpectedResponseType()),
                       () -> "Subscription Query query does not support Flux as a return type.");
        Assert.isFalse(Publisher.class.isAssignableFrom(query.updatesResponseType().getExpectedResponseType()),
                       () -> "Subscription Query query does not support Flux as an update type.");
    }

    @Nonnull
    private QueryHandler handlerFor(@Nonnull QueryMessage query) {
        ResponseType<?> responseType = query.responseType();
        QueryHandlerName handlerName = new QueryHandlerName(
                query.type().qualifiedName(),
                new QualifiedName(responseType.getExpectedResponseType())
        );
        if (!subscriptions.containsKey(handlerName)) {
            throw NoHandlerForQueryException.forBus(query);
        }
        return subscriptions.get(handlerName);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                              @Nonnull SubscriptionQueryUpdateMessage update,
                                              @Nullable ProcessingContext context) {
        return runAfterCommitOrImmediately(context, () -> doEmit(filter, update));
    }

    private void doEmit(Predicate<SubscriptionQueryMessage> filter,
                        SubscriptionQueryUpdateMessage update) {
        updateHandlers.entrySet()
                      .stream()
                      .filter(entry -> {
                          QualifiedName expectedUpdateName =
                                  new QualifiedName(entry.getKey().updatesResponseType().getExpectedResponseType());
                          return update.type().qualifiedName()
                                       .equals(expectedUpdateName);
                      })
                      .filter(entry -> filter.test(entry.getKey()))
                      .forEach(entry -> {
                          SubscriptionQueryMessage query = entry.getKey();
                          SinkWrapper<SubscriptionQueryUpdateMessage> updateHandler = entry.getValue();
                          try {
                              updateHandler.next(update);
                          } catch (Exception e) {
                              logger.info("An error occurred while trying to emit an update to a query '{}'. " +
                                                  "The subscription will be cancelled. Exception summary: {}",
                                          query.type(), e.toString());
                              updateHandlers.remove(query);
                              emitError(updateHandler, e, query);
                          }
                      });
    }

    @Override
    public CompletableFuture<Void> completeSubscription(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                        @Nullable ProcessingContext context) {
        return runAfterCommitOrImmediately(context, () -> doComplete(filter));
    }

    private void doComplete(Predicate<SubscriptionQueryMessage> filter) {
        updateHandlers.entrySet()
                      .stream()
                      .filter(entry -> filter.test(entry.getKey()))
                      .forEach(entry -> {
                          SinkWrapper<SubscriptionQueryUpdateMessage> updateHandler = entry.getValue();
                          try {
                              updateHandler.complete();
                          } catch (Exception e) {
                              emitError(updateHandler, e, entry.getKey());
                          }
                      });
    }

    @Override
    public CompletableFuture<Void> completeSubscriptionExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        return runAfterCommitOrImmediately(context, () -> doCompleteExceptionally(filter, cause));
    }

    @Nonnull
    private CompletableFuture<Void> runAfterCommitOrImmediately(@Nullable ProcessingContext context,
                                                                @Nonnull Runnable updateTask) {
        if (context != null) {
            context.computeResourceIfAbsent(
                           UPDATE_TASKS_KEY,
                           () -> {
                               List<Runnable> queryUpdateTasks = new ArrayList<>();
                               context.runOnAfterCommit(c -> queryUpdateTasks.forEach(Runnable::run));
                               return queryUpdateTasks;
                           }
                   )
                   .add(updateTask);
        } else {
            updateTask.run();
        }
        return FutureUtils.emptyCompletedFuture();
    }

    private void doCompleteExceptionally(Predicate<SubscriptionQueryMessage> filter, Throwable cause) {
        updateHandlers.entrySet()
                      .stream()
                      .filter(entry -> filter.test(entry.getKey()))
                      .forEach(entry -> emitError(entry.getValue(), cause, entry.getKey()));
    }

    private void emitError(SinkWrapper<?> updateHandler, Throwable cause, SubscriptionQueryMessage query) {
        try {
            updateHandler.error(cause);
        } catch (Exception e) {
            logger.error("An error happened while trying to inform update handler about the error. Query: {}", query);
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
        descriptor.describeProperty("subscriptions", subscriptions);
        descriptor.describeProperty("updateHandlers", updateHandlers);
    }
}
