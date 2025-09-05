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

package org.axonframework.messaging.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import java.util.List;

/**
 * A registry of {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, or {@link QueryMessage}-specific {@code MessageHandlerInterceptors}. Registered type
 * specific {@code MessageHandlerInterceptors} can be retrieved through {@link #commandInterceptors(Configuration)},
 * {@link #eventInterceptors(Configuration)}, and {@link #queryInterceptors(Configuration)}.
 * <p>
 * These operations are expected to be invoked within a {@link org.axonframework.configuration.DecoratorDefinition}. As
 * such, <b>any</b> registered interceptors are <b>only</b> applied when the infrastructure component requiring them is
 * constructed. When, for example, an {@link org.axonframework.commandhandling.InterceptingCommandBus} is constructed,
 * this registry is invoked to retrieve interceptors. Interceptors that are registered once the
 * {@code InterceptingCommandBus} has already been constructed are not taken into account.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public interface HandlerInterceptorRegistry extends DescribableComponent {

    /**
     * Registers the given {@code interceptorBuilder} for a generic {@link Message} {@link MessageHandlerInterceptor}.
     *
     * @param interceptorBuilder The generic {@link Message} {@link MessageHandlerInterceptor} builder to register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<Message>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link CommandMessage}-specific
     * {@link MessageHandlerInterceptor}.
     *
     * @param interceptorBuilder The {@link CommandMessage}-specific {@link MessageHandlerInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<CommandMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link EventMessage}-specific
     * {@link MessageHandlerInterceptor}.
     *
     * @param interceptorBuilder The {@link EventMessage}-specific {@link MessageHandlerInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<EventMessage>> interceptorBuilder
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link QueryMessage}-specific
     * {@link MessageHandlerInterceptor}.
     *
     * @param interceptorBuilder The {@link QueryMessage}-specific {@link MessageHandlerInterceptor} builder to
     *                           register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    HandlerInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<QueryMessage>> interceptorBuilder
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
    List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors(@Nonnull Configuration config);

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
    List<MessageHandlerInterceptor<EventMessage>> eventInterceptors(@Nonnull Configuration config);

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
    List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors(@Nonnull Configuration config);
}
