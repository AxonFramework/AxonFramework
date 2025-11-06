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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.interception.InterceptingCommandBus;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.*;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.List;

/**
 * A registry of {@link MessageDispatchInterceptor MessageDispatchInterceptors}, acting as a collection of
 * {@link ComponentRegistry#registerComponent(ComponentDefinition) registered
 * MessageDispatchInterceptors components}.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, {@link QueryMessage}-specific, or {@link SubscriptionQueryUpdateMessage}-specific
 * {@code MessageDispatchInterceptor}. Registered type specific {@code MessageDispatchInterceptor} can be retrieved
 * through {@link #commandInterceptors(Configuration)}, {@link #eventInterceptors(Configuration)},
 * {@link #queryInterceptors(Configuration)}, and {@link #subscriptionQueryUpdateInterceptors(Configuration)}.
 * <p>
 * These operations are expected to be invoked within a {@link DecoratorDefinition}. As
 * such, <b>any</b> registered interceptors are <b>only</b> applied when the infrastructure component requiring them is
 * constructed. When, for example, an {@link InterceptingCommandBus} is constructed,
 * this registry is invoked to retrieve interceptors. Interceptors that are registered once the
 * {@code InterceptingCommandBus} has already been constructed are not taken into ac
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public interface DispatchInterceptorRegistry extends DescribableComponent {

    /**
     * Registers the given {@code interceptorBuilder} for a generic {@link Message} {@link MessageDispatchInterceptor}.
     *
     * @param interceptorBuilder The generic {@link Message} {@link MessageDispatchInterceptor} builder to register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    DispatchInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<Message>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link CommandMessage}-specific
     * {@link MessageDispatchInterceptor}.
     *
     * @param interceptorBuilder The {@link CommandMessage}-specific {@link MessageDispatchInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    DispatchInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link EventMessage}-specific
     * {@link MessageDispatchInterceptor}.
     *
     * @param interceptorBuilder The {@link EventMessage}-specific {@link MessageDispatchInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    DispatchInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link QueryMessage}-specific
     * {@link MessageDispatchInterceptor}.
     *
     * @param interceptorBuilder The {@link QueryMessage}-specific {@link MessageDispatchInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    DispatchInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link SubscriptionQueryUpdateMessage}-specific
     * {@link MessageDispatchInterceptor}.
     *
     * @param interceptorBuilder The {@link SubscriptionQueryUpdateMessage}-specific {@link MessageDispatchInterceptor}
     *                           builder to register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    DispatchInterceptorRegistry registerSubscriptionQueryUpdateInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> interceptorBuilder
    );

    /**
     * Returns the list of {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry.
     *
     * @param config The configuration to build all {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     *               with.
     * @return The list of {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry.
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(@Nonnull Configuration config);

    /**
     * Returns the list of {@link EventMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     * registered in this registry.
     * <p>
     * This collection contains <b>all</b> generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(ComponentBuilder) registered} as well.
     *
     * @param config The configuration to build all {@link EventMessage}-specific
     *               {@link MessageDispatchInterceptor MessageDispatchInterceptors} with.
     * @return The list of {@link EventMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors(@Nonnull Configuration config);

    /**
     * Returns the list of {@link QueryMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     * registered in this registry.
     * <p>
     * This collection contains <b>all</b> generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(ComponentBuilder) registered} as well.
     *
     * @param config The configuration to build all {@link QueryMessage}-specific
     *               {@link MessageDispatchInterceptor MessageDispatchInterceptors} with.
     * @return The list of {@link QueryMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(@Nonnull Configuration config);

    /**
     * Returns the list of {@link SubscriptionQueryUpdateMessage}-specific
     * {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry.
     * <p>
     * This collection contains <b>all</b> generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(ComponentBuilder) registered} as well.
     *
     * @param config The configuration to build all {@link SubscriptionQueryUpdateMessage}-specific
     *               {@link MessageDispatchInterceptor MessageDispatchInterceptors} with.
     * @return The list of {@link SubscriptionQueryUpdateMessage}-specific
     *         {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors(@Nonnull Configuration config);
}
