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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * Implementation of {@link QueryUpdateEmitter} that uses Project Reactor to implement Update Handlers.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class SimpleQueryUpdateEmitter implements QueryUpdateEmitter {

    // TODO use logger on debug level for clarity
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryUpdateEmitter.class);

    private final ConcurrentMap<SubscriptionQueryMessage, SinkWrapper<SubscriptionQueryUpdateMessage>> updateHandlers =
            new ConcurrentHashMap<>();

    private final MessageTypeResolver messageTypeResolver;
    private final QueryBus queryBus;
    private final ProcessingContext context;

    /**
     * Construct a {@code SimpleQueryUpdateEmitter} with the given {@code messageTypeResolver} to construct
     * {@link SubscriptionQueryUpdateMessage update messages} for {@link #emit(Class, Predicate, Object)} invocations.
     *
     * @param messageTypeResolver The {@link org.axonframework.messaging.MessageType} resolver used to construct
     *                            {@link SubscriptionQueryUpdateMessage update messages} for
     *                            {@link #emit(Class, Predicate, Object)} invocations
     * @param queryBus
     * @param context
     */
    public SimpleQueryUpdateEmitter(@Nonnull MessageTypeResolver messageTypeResolver,
                                    @Nonnull QueryBus queryBus,
                                    @Nonnull ProcessingContext context) {
        this.messageTypeResolver =
                Objects.requireNonNull(messageTypeResolver, "The MessageTypeResolver must not be null.");
        this.queryBus = Objects.requireNonNull(queryBus, "The QueryBus must not be null.");
        this.context = Objects.requireNonNull(context, "The ProcessingContext must not be null.");
    }

    @Nonnull
    @Override
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

    @Override
    public <Q> void emit(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter, @Nullable Object update) {
        queryBus.emitUpdate(concretePayloadTypeFilter(queryType, filter), asUpdateMessage(update), context);
    }

    @Override
    public void emit(@Nonnull QualifiedName queryName,
                     @Nonnull Predicate<Object> filter,
                     @Nullable Object update) {
        Predicate<SubscriptionQueryMessage> messageFilter =
                message -> queryName.equals(message.type().qualifiedName()) && filter.test(message.payload());
        queryBus.emitUpdate(messageFilter, asUpdateMessage(update), context);
    }

    private SubscriptionQueryUpdateMessage asUpdateMessage(Object update) {
        if (update instanceof SubscriptionQueryUpdateMessage updateMessage) {
            return updateMessage;
        }
        return update instanceof Message updateMessage
                ? new GenericSubscriptionQueryUpdateMessage(updateMessage)
                : new GenericSubscriptionQueryUpdateMessage(messageTypeResolver.resolveOrThrow(update), update);
    }

    @Override
    public <Q> void complete(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter) {
        queryBus.completeSubscription(concretePayloadTypeFilter(queryType, filter), context);
    }

    @Override
    public <Q> void completeExceptionally(@Nonnull Class<Q> queryType,
                                          @Nonnull Predicate<? super Q> filter,
                                          @Nonnull Throwable cause) {
        queryBus.completeSubscriptionExceptionally(concretePayloadTypeFilter(queryType, filter), cause, context);
    }

    private <Q> Predicate<SubscriptionQueryMessage> concretePayloadTypeFilter(@Nonnull Class<Q> queryType,
                                                                              @Nonnull Predicate<? super Q> filter) {
        return message -> {
            QualifiedName queryName = messageTypeResolver.resolveOrThrow(queryType).qualifiedName();
            return queryName.equals(message.type().qualifiedName()) && filter.test(message.payloadAs(queryType));
        };
    }

    @Override
    public Set<SubscriptionQueryMessage> activeSubscriptions() {
        return Collections.unmodifiableSet(updateHandlers.keySet());
    }
}
