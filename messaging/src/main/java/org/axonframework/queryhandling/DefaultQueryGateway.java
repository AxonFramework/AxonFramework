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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
     * The {@link org.axonframework.messaging.QualifiedName names} for {@link QueryMessage QueryMessages} are resolved
     * through the given {@code nameResolver}.
     *
     * @param queryBus            The {@link QueryBus} to send queries on.
     * @param messageTypeResolver The {@link MessageTypeResolver} resolving the
     *                            {@link org.axonframework.messaging.QualifiedName names} for
     *                            {@link QueryMessage QueryMessages} being dispatched on the {@code queryBus}.
     * @param priorityCalculator  The {@link QueryPriorityCalculator} determining the priority of queries.
     */
    public DefaultQueryGateway(@Nonnull QueryBus queryBus,
                               @Nonnull MessageTypeResolver messageTypeResolver,
                               @Nonnull QueryPriorityCalculator priorityCalculator) {
        this.queryBus = Objects.requireNonNull(queryBus, "The QueryBus must not be null.");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver,
                                                          "The MessageTypeResolver must not be null.");
        this.priorityCalculator = Objects.requireNonNull(priorityCalculator,
                                                         "The QueryPriorityCalculator must not be null.");
    }

    @Nonnull
    @Override
    public <R> CompletableFuture<R> query(@Nonnull Object query,
                                          @Nonnull Class<R> responseType,
                                          @Nullable ProcessingContext context) {
        QueryMessage queryMessage = asQueryMessage(query, ResponseTypes.instanceOf(responseType));
        MessageStream<QueryResponseMessage> resultStream = queryBus.query(queryMessage, context);
        CompletableFuture<R> resultFuture =
                resultStream.first()
                            .asCompletableFuture()
                            .thenApply(MessageStream.Entry::message)
                            .thenApply(queryResponseMessage -> queryResponseMessage.payloadAs(responseType));
        // We cannot chain the whenComplete call, as otherwise CompletableFuture#cancel is not propagated to the lambda.
        resultFuture.whenComplete((r, e) -> {
            if (!resultStream.isCompleted()) {
                resultStream.close();
            }
        });
        return resultFuture;
    }

    @Nonnull
    @Override
    public <R> CompletableFuture<List<R>> queryMany(@Nonnull Object query,
                                                    @Nonnull Class<R> responseType,
                                                    @Nullable ProcessingContext context) {
        QueryMessage queryMessage = asQueryMessage(query, ResponseTypes.multipleInstancesOf(responseType));
        MessageStream<QueryResponseMessage> resultStream = queryBus.query(queryMessage, context);
        CompletableFuture<List<R>> resultFuture =
                resultStream.reduce(new ArrayList<>(), (list, entry) -> {
                    list.add(entry.message().payloadAs(responseType));
                    return list;
                });
        // We cannot chain the whenComplete call, as otherwise CompletableFuture#cancel is not propagated to the lambda.
        resultFuture.whenComplete((r, e) -> {
            if (!resultStream.isCompleted()) {
                resultStream.close();
            }
        });
        return resultFuture;
    }

    @Nonnull
    @Override
    public <R> Publisher<R> streamingQuery(@Nonnull Object query,
                                           @Nonnull Class<R> responseType,
                                           @Nullable ProcessingContext context) {
        return Mono.fromSupplier(() -> asStreamingQueryMessage(query, responseType))
                   .flatMapMany(queryMessage -> queryBus.streamingQuery(queryMessage, context))
                   .mapNotNull(m -> m.payloadAs(responseType));
    }

    @Nonnull
    @Override
    public <R> Publisher<R> subscriptionQuery(@Nonnull Object query,
                                              @Nonnull Class<R> responseType,
                                              @Nullable ProcessingContext context) {
        SubscriptionQueryMessage queryMessage =
                asSubscriptionQueryMessage(query,
                                           ResponseTypes.instanceOf(responseType),
                                           ResponseTypes.instanceOf(responseType));
        return queryBus.subscriptionQuery(queryMessage, context, Queues.SMALL_BUFFER_SIZE)
                       .asFlux()
                       .mapNotNull(message -> message.payloadAs(responseType));
    }

    @Nonnull
    @Override
    public <I, U> SubscriptionQueryResponse<I, U> subscriptionQuery(@Nonnull Object query,
                                                                    @Nonnull Class<I> initialResponseType,
                                                                    @Nonnull Class<U> updateResponseType,
                                                                    @Nullable ProcessingContext context,
                                                                    int updateBufferSize) {
        SubscriptionQueryMessage queryMessage =
                asSubscriptionQueryMessage(query,
                                           ResponseTypes.instanceOf(initialResponseType),
                                           ResponseTypes.instanceOf(updateResponseType));
        SubscriptionQueryResponseMessages response = queryBus.subscriptionQuery(queryMessage,
                                                                                context,
                                                                                updateBufferSize);
        return new GenericSubscriptionQueryResponse<>(response,
                                                      message -> message.payloadAs(initialResponseType),
                                                      message -> message.payloadAs(updateResponseType));
    }


    private QueryMessage asQueryMessage(Object query, ResponseType<?> responseType) {
        return query instanceof Message message
                ? new GenericQueryMessage(message, responseType)
                : new GenericQueryMessage(resolveType(query), query, responseType);
    }

    private <R> StreamingQueryMessage asStreamingQueryMessage(Object query, Class<R> responseType) {
        return query instanceof Message message
                ? new GenericStreamingQueryMessage(message, responseType)
                : new GenericStreamingQueryMessage(resolveType(query), query, responseType);
    }

    private <I, U> SubscriptionQueryMessage asSubscriptionQueryMessage(Object query,
                                                                       ResponseType<I> initialType,
                                                                       ResponseType<U> updateType) {
        return query instanceof Message
                ? new GenericSubscriptionQueryMessage((Message) query, initialType, updateType)
                : new GenericSubscriptionQueryMessage(resolveType(query), query, initialType, updateType);
    }

    private MessageType resolveType(Object query) {
        return messageTypeResolver.resolveOrThrow(query);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("queryBus", queryBus);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
        descriptor.describeProperty("priorityCalculator", priorityCalculator);
    }
}
