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
import org.axonframework.commandhandling.gateway.CommandDispatcher;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageTypeNotResolvedException;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Component which informs
 * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries} about
 * updates, errors, and when there are no more updates.
 * <p>
 * If any of the emitter functions in this interface are called from a message handling function (e.g. an
 * {@link org.axonframework.eventhandling.EventHandler}), then that call will automatically be tied into the lifecycle
 * of the {@link ProcessingContext} to ensure correct order of execution.
 * <p>
 *
 * @author Milan Savic
 * @since 3.3.0
 */ // TODO make it a DescribableComponent
public interface QueryUpdateEmitter {

    /**
     * The {@link ResourceKey} used to store the {@link CommandDispatcher} in the {@link ProcessingContext}.
     */
    ResourceKey<QueryUpdateEmitter> RESOURCE_KEY = ResourceKey.withLabel("QueryUpdateEmitter");

    /**
     * TODO
     */
    static QueryUpdateEmitter forContext(@Nonnull ProcessingContext context) {
        return context.computeResourceIfAbsent(
                RESOURCE_KEY,
                () -> new SimpleQueryUpdateEmitter(
                        context.component(MessageTypeResolver.class),
                        context.component(QueryBus.class),
                        context
                )
        );
    }

    /**
     * Subscribes the given {@code query} with the given {@code updateBufferSize}, resulting in an {@link UpdateHandler}
     * providing a {@link reactor.core.publisher.Flux} to the emitted updates.
     *
     * @param query            The subscription query for which we register an update handler.
     * @param updateBufferSize The size of buffer that accumulates updates before a subscription to the
     *                         {@link UpdateHandler#updates()} is made.
     * @return The update handler containing the {@link reactor.core.publisher.Flux} of emitted updates, as well as
     * {@link UpdateHandler#cancel()} and {@link UpdateHandler#complete()} hooks.
     * @throws SubscriptionQueryAlreadyRegisteredException Whenever an update handler was already registered for the
     *                                                     given {@code query}.
     */
    @Internal
    @Nonnull
    UpdateHandler subscribe(@Nonnull SubscriptionQueryMessage query, int updateBufferSize);

    /**
     * Emits given incremental {@code update} to subscription queries matching the given {@code queryType} and given
     * {@code filter}.
     *
     * @param queryType The type of the {@link SubscriptionQueryMessage} to filter on.
     * @param filter    A predicate testing the {@link SubscriptionQueryMessage#payload()}, converted to the given
     *                  {@code queryType} to filter on.
     * @param update    The incremental update to emit for
     *                  {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription
     *                  queries} matching the given {@code filter}.
     * @param <Q>       The type of the {@link SubscriptionQueryMessage} to filter on.
     * @throws MessageTypeNotResolvedException                     If the given {@code queryType} has no known
     *                                                             {@link org.axonframework.messaging.MessageType}
     *                                                             equivalent required to filter the
     *                                                             {@link SubscriptionQueryMessage#payload()}.
     * @throws org.axonframework.serialization.ConversionException If the {@link SubscriptionQueryMessage#payload()}
     *                                                             could not be converted to the given {@code queryType}
     *                                                             to perform the given {@code filter}. Will only occur
     *                                                             if a {@link org.axonframework.messaging.MessageType}
     *                                                             could be found for the given {@code queryType}.
     */
    <Q> void emit(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter, @Nullable Object update);

    /**
     * Emits given incremental {@code update} to subscription queries matching the given {@code queryName} and given
     * {@code filter}.
     *
     * @param queryName The qualified name of the {@link SubscriptionQueryMessage#type()} to filter on.
     * @param filter    A predicate testing the {@link SubscriptionQueryMessage#payload()} as is to the given
     *                  {@code queryType} to filter on.
     * @param update    The incremental update to emit for
     *                  {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription
     *                  queries} matching the given {@code filter}.
     */
    void emit(@Nonnull QualifiedName queryName, @Nonnull Predicate<Object> filter, @Nullable Object update);

    /**
     * TODO JavaDoc
     * Completes subscription queries matching given query type and filter.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param <Q>       the type of the query
     * @throws MessageTypeNotResolvedException                     If the given {@code queryType} has no known
     *                                                             {@link org.axonframework.messaging.MessageType}
     *                                                             equivalent required to filter the
     *                                                             {@link SubscriptionQueryMessage#payload()}.
     * @throws org.axonframework.serialization.ConversionException If the {@link SubscriptionQueryMessage#payload()}
     *                                                             could not be converted to the given {@code queryType}
     *                                                             to perform the given {@code filter}. Will only occur
     *                                                             if a {@link org.axonframework.messaging.MessageType}
     *                                                             could be found for the given {@code queryType}.
     */
    <Q> void complete(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter);

    /**
     * TODO JavaDoc
     * Completes with an error subscription queries matching given query type and filter
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param cause     the cause of an error
     * @param <Q>       the type of the query
     * @throws MessageTypeNotResolvedException                     If the given {@code queryType} has no known
     *                                                             {@link org.axonframework.messaging.MessageType}
     *                                                             equivalent required to filter the
     *                                                             {@link SubscriptionQueryMessage#payload()}.
     * @throws org.axonframework.serialization.ConversionException If the {@link SubscriptionQueryMessage#payload()}
     *                                                             could not be converted to the given {@code queryType}
     *                                                             to perform the given {@code filter}. Will only occur
     *                                                             if a {@link org.axonframework.messaging.MessageType}
     *                                                             could be found for the given {@code queryType}.
     */
    <Q> void completeExceptionally(@Nonnull Class<Q> queryType,
                                   @Nonnull Predicate<? super Q> filter,
                                   @Nonnull Throwable cause);

    /**
     * Provides the set of running subscription queries. If there are changes to subscriptions they will be reflected in
     * the returned set of this method. Implementations should provide an unmodifiable set of the active subscriptions.
     *
     * @return the set of running subscription queries
     */
    default Set<SubscriptionQueryMessage> activeSubscriptions() {
        return Collections.emptySet();
    }
}
