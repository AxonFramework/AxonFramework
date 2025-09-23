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
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageTypeNotResolvedException;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.function.Predicate;

/**
 * Query-specific component that interacts with
 * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries} about
 * {@link #emit(Class, Predicate, Object) updates}, {@link #completeExceptionally(Class, Predicate, Throwable) errors},
 * and when there are {@link #complete(Class, Predicate) no more updates}.
 * <p>
 * Implementations of the {@code QueryUpdateEmitter} are expected to be {@link ProcessingContext context-aware}, to
 * ensure operations occur within the correct order of (for example) the lifecycle of an
 * {@link org.axonframework.eventhandling.EventHandler event handling function.}
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3.0
 */
public interface QueryUpdateEmitter extends DescribableComponent {

    /**
     * The {@link ResourceKey} used to store the {@link QueryUpdateEmitter} in the {@link ProcessingContext}.
     */
    ResourceKey<QueryUpdateEmitter> RESOURCE_KEY = ResourceKey.withLabel("QueryUpdateEmitter");

    /**
     * Creates a query update emitter for the given {@link ProcessingContext}.
     * <p>
     * You can use this emitter <b>only</b> for the context it was created for. There is no harm in using this method
     * more than once with the same {@code context}, as the same emitter will be returned.
     *
     * @param context The {@link ProcessingContext} to create the emitter for.
     * @return The emitter specific for the given {@code context}.
     */
    static QueryUpdateEmitter forContext(@Nonnull ProcessingContext context) {
        return context.computeResourceIfAbsent(
                RESOURCE_KEY,
                () -> new SimpleQueryUpdateEmitter(
                        context.component(QueryBus.class),
                        context.component(MessageTypeResolver.class),
                        context.component(MessageConverter.class),
                        context
                )
        );
    }

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
     * Completes subscription queries matching the given {@code queryType} and {@code filter}.
     *
     * @param queryType The type of the {@link SubscriptionQueryMessage} to filter on.
     * @param filter    A predicate testing the {@link SubscriptionQueryMessage#payload()}, converted to the given
     *                  {@code queryType} to filter on.
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
    <Q> void complete(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter);

    /**
     * Completes subscription queries matching the given {@code queryName} and {@code filter}.
     *
     * @param queryName The qualified name of the {@link SubscriptionQueryMessage#type()} to filter on.
     * @param filter    A predicate testing the {@link SubscriptionQueryMessage#payload()} as is to the given
     *                  {@code queryType} to filter on.
     */
    void complete(@Nonnull QualifiedName queryName, @Nonnull Predicate<Object> filter);

    /**
     * Completes subscription queries with the given {@code cause} matching given {@code queryType} and {@code filter}.
     *
     * @param queryType The type of the {@link SubscriptionQueryMessage} to filter on.
     * @param filter    A predicate testing the {@link SubscriptionQueryMessage#payload()}, converted to the given
     *                  {@code queryType} to filter on.
     * @param cause     The cause of an error leading to exceptionally complete subscription queries.
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
    <Q> void completeExceptionally(@Nonnull Class<Q> queryType,
                                   @Nonnull Predicate<? super Q> filter,
                                   @Nonnull Throwable cause);

    /**
     * Completes subscription queries with the given {@code cause} matching given {@code queryName} and {@code filter}.
     *
     * @param queryName The qualified name of the {@link SubscriptionQueryMessage#type()} to filter on.
     * @param filter    A predicate testing the {@link SubscriptionQueryMessage#payload()} as is to the given
     *                  {@code queryType} to filter on.
     * @param cause     The cause of an error leading to exceptionally complete subscription queries.
     */
    void completeExceptionally(@Nonnull QualifiedName queryName,
                               @Nonnull Predicate<Object> filter,
                               @Nonnull Throwable cause);
}
