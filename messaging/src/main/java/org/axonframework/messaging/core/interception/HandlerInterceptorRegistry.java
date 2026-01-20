/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.queryhandling.QueryMessage;

import java.util.List;

/**
 * A registry of {@link MessageHandlerInterceptor MessageHandlerInterceptors}, acting as a collection of
 * {@link ComponentRegistry#registerComponent(ComponentDefinition) registered
 * MessageHandlerInterceptors components}.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, or {@link QueryMessage}-specific {@code MessageHandlerInterceptors}. Registered type
 * specific {@code MessageHandlerInterceptors} can be retrieved through {@link #commandInterceptors(Configuration)},
 * {@link #eventInterceptors(Configuration)}, and {@link #queryInterceptors(Configuration)}.
 * <p>
 * These operations are expected to be invoked within a {@link DecoratorDefinition}. As
 * such, <b>any</b> registered interceptors are <b>only</b> applied when the infrastructure component requiring them is
 * constructed. When, for example, an {@link InterceptingCommandBus} is constructed,
 * this registry is invoked to retrieve interceptors. Interceptors that are registered once the
 * {@code InterceptingCommandBus} has already been constructed are not taken into account.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public interface HandlerInterceptorRegistry extends DescribableComponent {

    /**
     * Registers the given {@code interceptorBuilder} constructing a generic {@link Message}
     * {@link MessageHandlerInterceptor} for all handling infrastructure components.
     *
     * @param interceptorBuilder The generic {@link Message} {@link MessageHandlerInterceptor} builder to register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<Message>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link CommandMessage} {@link MessageHandlerInterceptor} for
     * all command handling infrastructure components.
     *
     * @param interceptorBuilder The {@link CommandMessage}-specific {@link MessageHandlerInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super CommandMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link EventMessage} {@link MessageHandlerInterceptor} for
     * all event handling infrastructure components
     *
     * @param interceptorBuilder The {@link EventMessage}-specific {@link MessageHandlerInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super EventMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link QueryMessage} {@link MessageHandlerInterceptor} for
     * all query handling infrastructure components
     *
     * @param interceptorBuilder The {@link QueryMessage}-specific {@link MessageHandlerInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super QueryMessage>> interceptorBuilder
    );

    /**
     * Returns the list of {@link CommandMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * registered in this registry.
     * <p>
     * This collection contains <b>all</b> generic {@link Message} {@code MessageHandlerInterceptors} that have been
     * {@link #registerInterceptor(ComponentBuilder) registered} as well.
     *
     * @param config The configuration to build all {@link CommandMessage}-specific
     *               {@link MessageHandlerInterceptor MessageHandlerInterceptors} with.
     * @return The list of {@link CommandMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
     */
    @Nonnull
    List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors(@Nonnull Configuration config);

    /**
     * Returns the list of {@link EventMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * registered in this registry.
     * <p>
     * This collection contains <b>all</b> generic {@link Message} {@code MessageHandlerInterceptors} that have been
     * {@link #registerInterceptor(ComponentBuilder) registered} as well.
     *
     * @param config The configuration to build all {@link EventMessage}-specific
     *               {@link MessageHandlerInterceptor MessageHandlerInterceptors} with.
     * @return The list of {@link EventMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
     */
    @Nonnull
    List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors(@Nonnull Configuration config);

    /**
     * Returns the list of {@link QueryMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * registered in this registry.
     * <p>
     * This collection contains <b>all</b> generic {@link Message} {@code MessageHandlerInterceptors} that have been
     * {@link #registerInterceptor(ComponentBuilder) registered} as well.
     *
     * @param config The configuration to build all {@link QueryMessage}-specific
     *               {@link MessageHandlerInterceptor MessageHandlerInterceptors} with.
     * @return The list of {@link QueryMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
     */
    @Nonnull
    List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors(@Nonnull Configuration config);
}
