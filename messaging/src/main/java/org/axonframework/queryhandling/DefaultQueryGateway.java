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
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Default implementation of the {@link QueryGateway} interface.
 *
 * @author Allard Buijze
 * @author Marc Gathier
 * @author Steven van Beelen
 * @since 3.1.0
 */
public class DefaultQueryGateway implements QueryGateway {

    private final QueryBus queryBus;
    private final MessageTypeResolver messageTypeResolver;
    private final QueryPriorityCalculator priorityCalculator;

    /**
     * Initialize the {@code DefaultQueryGateway} to send queries through the given {@code queryBus}.
     * <p>
     * The {@link org.axonframework.messaging.QualifiedName names} for
     * {@link org.axonframework.commandhandling.CommandMessage CommandMessages} are resolved through the given
     * {@code nameResolver}.
     *
     * @param queryBus            The {@link QueryBus} to send queries on.
     * @param messageTypeResolver The {@link MessageTypeResolver} resolving the
     *                            {@link org.axonframework.messaging.QualifiedName names} for
     *                            {@link QueryMessage QueryMessages} being dispatched on the {@code queryBus}.
     * @param priorityCalculator  The {@link CommandPriorityCalculator} determining the priority of commands. Can be
     *                            omitted.
     */
    public DefaultQueryGateway(@Nonnull QueryBus queryBus,
                               @Nonnull MessageTypeResolver messageTypeResolver,
                               @Nullable QueryPriorityCalculator priorityCalculator) {
        this.queryBus = Objects.requireNonNull(queryBus, "The QueryBus must not be null.");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver,
                                                          "The MessageTypeResolver must not be null.");
        this.priorityCalculator = priorityCalculator;
    }

    @Nonnull
    @Override
    public <R, Q> CompletableFuture<R> query(@Nonnull Q query,
                                             @Nonnull ResponseType<R> responseType) {
        QueryMessage queryMessage = asQueryMessage(query, responseType);

        // TODO replace for response type mapping here
        //= queryBus.query(queryMessage);
        CompletableFuture<QueryResponseMessage> queryResponse = null;
        CompletableFuture<R> result = new CompletableFuture<>();
        result.whenComplete((r, e) -> {
            if (!queryResponse.isDone()) {
                queryResponse.cancel(true);
            }
        });
        queryResponse.exceptionally(cause -> asResponseMessage(responseType.responseMessagePayloadType(), cause))
                     .thenAccept(queryResponseMessage -> {
                         try {
                             if (queryResponseMessage.isExceptional()) {
                                 result.completeExceptionally(queryResponseMessage.exceptionResult());
                             } else {
                                 result.complete((R) queryResponseMessage.payload());
                             }
                         } catch (Exception e) {
                             result.completeExceptionally(e);
                         }
                     });
        return result;
    }

    /**
     * Creates a Query Response Message with given {@code declaredType} and {@code exception}.
     *
     * @param declaredType The declared type of the Query Response Message to be created
     * @param exception    The Exception describing the cause of an error
     * @param <R>          The type of the payload
     * @return a message containing exception result
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    private <R> QueryResponseMessage asResponseMessage(Class<R> declaredType, Throwable exception) {
        return new GenericQueryResponseMessage(messageTypeResolver.resolveOrThrow(exception.getClass()),
                                               exception,
                                               declaredType);
    }

    @Nonnull
    @Override
    public <R, Q> Publisher<R> streamingQuery(@Nonnull Q query, @Nonnull Class<R> responseType) {
        return Mono.fromSupplier(() -> asStreamingQueryMessage(query, responseType))
                   .flatMapMany(queryMessage -> queryBus.streamingQuery(queryMessage))
                   .map(m -> (R) m.payload());
    }

    private <R, Q> StreamingQueryMessage asStreamingQueryMessage(Q query,
                                                                 Class<R> responseType) {
        return query instanceof Message
                ? new GenericStreamingQueryMessage((Message) query, responseType)
                : new GenericStreamingQueryMessage(messageTypeResolver.resolveOrThrow(query), query, responseType);
    }

    @Nonnull
    @Override
    public <R, Q> Stream<R> scatterGather(@Nonnull Q query,
                                          @Nonnull ResponseType<R> responseType,
                                          long timeout,
                                          @Nonnull TimeUnit timeUnit) {
        QueryMessage queryMessage = asQueryMessage(query, responseType);
        return queryBus.scatterGather(queryMessage, timeout, timeUnit)
                       .map(t -> (R) t.payload());
    }

    @Nonnull
    @Override
    public <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull Q query,
                                                                     @Nonnull ResponseType<I> initialResponseType,
                                                                     @Nonnull ResponseType<U> updateResponseType,
                                                                     int updateBufferSize) {
        SubscriptionQueryMessage<?, I, U> subscriptionQueryMessage =
                asSubscriptionQueryMessage(query, initialResponseType, updateResponseType);

        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                queryBus.subscriptionQuery(subscriptionQueryMessage, updateBufferSize);

        return getSubscriptionQueryResult(result);
    }

    private <R, Q> QueryMessage asQueryMessage(Q query,
                                               ResponseType<R> responseType) {
        return query instanceof Message
                ? new GenericQueryMessage((Message) query, responseType)
                : new GenericQueryMessage(messageTypeResolver.resolveOrThrow(query), query, responseType);
    }

    private <Q, I, U> SubscriptionQueryMessage<Q, I, U> asSubscriptionQueryMessage(
            Q query,
            ResponseType<I> initialResponseType,
            ResponseType<U> updateResponseType
    ) {
        return query instanceof Message
                ? new GenericSubscriptionQueryMessage<>((Message) query,
                                                        initialResponseType,
                                                        updateResponseType)
                : new GenericSubscriptionQueryMessage<>(messageTypeResolver.resolveOrThrow(query),
                                                        query,
                                                        initialResponseType,
                                                        updateResponseType);
    }

    private <I, U> DefaultSubscriptionQueryResult<I, U> getSubscriptionQueryResult(
            SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result
    ) {
        return new DefaultSubscriptionQueryResult<>(
                result.initialResult()
                      .filter(initialResult -> Objects.nonNull(initialResult.payload()))
                      .map(t -> (I) t.payload())
                      .onErrorMap(e -> e instanceof IllegalPayloadAccessException ? e.getCause() : e),
                result.updates()
                      .filter(update -> Objects.nonNull(update.payload()))
                      .map(t -> (U) t.payload()),
                result
        );
    }
}
