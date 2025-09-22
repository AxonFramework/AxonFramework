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

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementation of the {@link QueryUpdateEmitter}, delegating operations to a {@link QueryBus} for emitting
 * updates, completing subscription queries, and completing subscription queries exceptionally.
 * <p>
 * Uses the {@link ProcessingContext} given during construction of the {@code SimpleQueryUpdateEmitter} to perform all
 * operations in the expected lifecycle order.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class SimpleQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryUpdateEmitter.class);

    private final MessageTypeResolver messageTypeResolver;
    private final QueryBus queryBus;
    private final ProcessingContext context;

    /**
     * Construct a {@code SimpleQueryUpdateEmitter} with the given {@code messageTypeResolver}, {@code queryBus}, and
     * {@code context}.
     * <p>
     * The {@code messageTypeResolver} is used to construct {@link SubscriptionQueryUpdateMessage update messages} for
     * {@link #emit(Class, Predicate, Object)} invocations. Any {@link #emit(Class, Predicate, Object)} or
     * {@link #emit(QualifiedName, Predicate, Object)} is delegated to
     * {@link QueryBus#emitUpdate(Predicate, SubscriptionQueryUpdateMessage, ProcessingContext)}. Similarly,
     * {@link #complete(Class, Predicate)}/{@link #complete(QualifiedName, Predicate)} and
     * {@link #completeExceptionally(Class, Predicate, Throwable)}/{@link #completeExceptionally(QualifiedName,
     * Predicate, Throwable)} are respectively delegated to
     * {@link QueryBus#completeSubscription(Predicate, ProcessingContext)} and
     * {@link QueryBus#completeSubscriptionExceptionally(Predicate, Throwable, ProcessingContext)}.
     *
     * @param messageTypeResolver The {@link org.axonframework.messaging.MessageType} resolver used to construct
     *                            {@link SubscriptionQueryUpdateMessage update messages} for
     *                            {@link #emit(Class, Predicate, Object)} invocations
     * @param queryBus            The {@code QueryBus} to delegate the {@link #emit(Class, Predicate, Object)},
     *                            {@link #complete(Class, Predicate)}, and
     *                            {@link #completeExceptionally(Class, Predicate, Throwable)} invocations to.
     * @param context             The {@code ProcessingContext} within which updates are emitted, subscription query are
     *                            completed, and subscription queries are completed exceptionally in.
     */
    public SimpleQueryUpdateEmitter(@Nonnull MessageTypeResolver messageTypeResolver,
                                    @Nonnull QueryBus queryBus,
                                    @Nonnull ProcessingContext context) {
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver must not be null.");
        this.queryBus = requireNonNull(queryBus, "The QueryBus must not be null.");
        this.context = requireNonNull(context, "The ProcessingContext must not be null.");
    }

    @Override
    public <Q> void emit(@Nonnull Class<Q> queryType,
                         @Nonnull Predicate<? super Q> filter,
                         @Nullable Object update) {
        if (logger.isDebugEnabled()) {
            logger.debug("Emitting update [{}] for query of type [{}].", update, queryType);
        }
        queryBus.emitUpdate(concretePayloadTypeFilter(queryType, filter), asUpdateMessage(update), context)
                .join();
    }

    @Override
    public void emit(@Nonnull QualifiedName queryName,
                     @Nonnull Predicate<Object> filter,
                     @Nullable Object update) {
        if (logger.isDebugEnabled()) {
            logger.debug("Emitting update [{}] for query with name [{}].", update, queryName);
        }
        queryBus.emitUpdate(queryNameFilter(queryName, filter), asUpdateMessage(update), context)
                .join();
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
        if (logger.isDebugEnabled()) {
            logger.debug("Completing subscription queries of type [{}].", queryType);
        }
        queryBus.completeSubscription(concretePayloadTypeFilter(queryType, filter), context)
                .join();
    }

    @Override
    public void complete(@Nonnull QualifiedName queryName, @Nonnull Predicate<Object> filter) {
        if (logger.isDebugEnabled()) {
            logger.debug("Completing subscription queries with name [{}].", queryName);
        }
        queryBus.completeSubscription(queryNameFilter(queryName, filter), context)
                .join();
    }

    @Override
    public <Q> void completeExceptionally(@Nonnull Class<Q> queryType,
                                          @Nonnull Predicate<? super Q> filter,
                                          @Nonnull Throwable cause) {
        if (logger.isDebugEnabled()) {
            logger.debug("Completing subscription queries of type [{}] exceptionally.", queryType, cause);
        }
        queryBus.completeSubscriptionExceptionally(concretePayloadTypeFilter(queryType, filter), cause, context)
                .join();
    }

    @Override
    public void completeExceptionally(@Nonnull QualifiedName queryName,
                                      @Nonnull Predicate<Object> filter,
                                      @Nonnull Throwable cause) {
        if (logger.isDebugEnabled()) {
            logger.debug("Completing subscription queries with name [{}] exceptionally.", queryName, cause);
        }
        queryBus.completeSubscriptionExceptionally(queryNameFilter(queryName, filter), cause, context)
                .join();
    }

    @Nonnull
    private static Predicate<SubscriptionQueryMessage> queryNameFilter(@Nonnull QualifiedName queryName,
                                                                       @Nonnull Predicate<Object> filter) {
        return message -> queryName.equals(message.type().qualifiedName()) && filter.test(message.payload());
    }

    private <Q> Predicate<SubscriptionQueryMessage> concretePayloadTypeFilter(@Nonnull Class<Q> queryType,
                                                                              @Nonnull Predicate<? super Q> filter) {
        return message -> {
            QualifiedName queryName = messageTypeResolver.resolveOrThrow(queryType).qualifiedName();
            return queryName.equals(message.type().qualifiedName()) && filter.test(message.payloadAs(queryType));
        };
    }
}
