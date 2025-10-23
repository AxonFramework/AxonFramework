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
import org.axonframework.messaging.FluxUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Converter;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
    private final MessageConverter converter;

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
     * @param converter           The converter to use for converting the result of query handling.
     */
    public DefaultQueryGateway(@Nonnull QueryBus queryBus,
                               @Nonnull MessageTypeResolver messageTypeResolver,
                               @Nonnull QueryPriorityCalculator priorityCalculator,
                               @Nonnull MessageConverter converter) {
        this.queryBus = Objects.requireNonNull(queryBus, "The QueryBus must not be null.");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver,
                                                          "The MessageTypeResolver must not be null.");
        this.priorityCalculator = Objects.requireNonNull(priorityCalculator,
                                                         "The QueryPriorityCalculator must not be null.");
        this.converter = Objects.requireNonNull(converter, "The MessageConverter must not be null.");
    }

    @Nonnull
    @Override
    public <R> CompletableFuture<R> query(@Nonnull Object query,
                                          @Nonnull Class<R> responseType,
                                          @Nullable ProcessingContext context) {
        QueryMessage queryMessage = asQueryMessage(query, responseType);
        MessageStream<QueryResponseMessage> resultStream = queryBus.query(queryMessage, context);
        CompletableFuture<R> resultFuture =
                resultStream.first()
                            .asCompletableFuture()
                            .thenApply(MessageStream.Entry::message)
                            .thenApply(queryResponseMessage -> {
                                if (queryResponseMessage == null) { // in the case of MessageStream.Empty
                                    return null;
                                }
                                return queryResponseMessage.payloadAs(responseType, converter);
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
    public <R> CompletableFuture<List<R>> queryMany(@Nonnull Object query,
                                                    @Nonnull Class<R> responseType,
                                                    @Nullable ProcessingContext context) {
        QueryMessage queryMessage = asQueryMessage(query, responseType);
        MessageStream<QueryResponseMessage> resultStream = queryBus.query(queryMessage, context);
        CompletableFuture<List<R>> resultFuture =
                resultStream.reduce(new ArrayList<>(), (list, entry) -> {
                    list.add(entry.message().payloadAs(responseType, converter));
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
        return Mono.fromSupplier(() -> asQueryMessage(query, responseType))
                   .flatMapMany(queryMessage -> FluxUtils.of(queryBus.query(queryMessage, context)))
                   .map(MessageStream.Entry::message)
                   .mapNotNull(m -> m.payloadAs(responseType, converter));
    }

    @Nonnull
    @Override
    public <R> Publisher<R> subscriptionQuery(@Nonnull Object query,
                                              @Nonnull Class<R> responseType,
                                              @Nullable ProcessingContext context,
                                              int updateBufferSize) {
        return subscriptionQuery(query,
                                 responseType,
                                 m -> m.payloadAs(responseType, converter),
                                 context,
                                 updateBufferSize);
    }

    @Nonnull
    @Override
    public <T> Publisher<T> subscriptionQuery(@Nonnull Object query,
                                              @Nonnull Class<T> responseType,
                                              @Nonnull Function<QueryResponseMessage, T> mapper,
                                              @Nullable ProcessingContext context,
                                              int updateBufferSize) {
        SubscriptionQueryMessage queryMessage = asSubscriptionQueryMessage(query,
                                                                           resolveTypeFor(responseType));
        MessageStream<QueryResponseMessage> response = queryBus.subscriptionQuery(queryMessage,
                                                                                  context,
                                                                                  updateBufferSize);
        return FluxUtils.of(response)
                        .mapNotNull(m -> mapper.apply(m.message()))
                        .doOnCancel(response::close)
                        .doOnError((e) -> response.close());
    }

    private QueryMessage asQueryMessage(Object query, Class<?> responseType) {
        if (query instanceof QueryMessage queryMessage) {
            return queryMessage;
        }
        return query instanceof Message message
                ? new GenericQueryMessage(message, resolveTypeFor(responseType))
                : new GenericQueryMessage(resolveTypeFor(query), query, resolveTypeFor(responseType));
    }

    private SubscriptionQueryMessage asSubscriptionQueryMessage(Object query,
                                                                MessageType messageType) {
        if (query instanceof SubscriptionQueryMessage queryMessage) {
            return queryMessage;
        }
        return query instanceof Message
                ? new GenericSubscriptionQueryMessage((Message) query, messageType)
                : new GenericSubscriptionQueryMessage(resolveTypeFor(query),
                                                      query,
                                                      messageType);
    }

    private MessageType resolveTypeFor(Object payload) {
        return messageTypeResolver.resolveOrThrow(payload);
    }

    private MessageType resolveTypeFor(Class<?> clazz) {
        return messageTypeResolver.resolveOrThrow(clazz);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("queryBus", queryBus);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
        descriptor.describeProperty("priorityCalculator", priorityCalculator);
    }
}
