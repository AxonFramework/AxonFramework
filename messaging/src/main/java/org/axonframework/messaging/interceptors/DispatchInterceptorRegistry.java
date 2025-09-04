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
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.List;

/**
 * A registry of {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
 * <p>
 * Provides an operation to register a generic {@link Message} {@code MessageHandlerInterceptor}. Registered
 * {@code MessageDispatchInterceptors} can be retrieved through {@link #interceptors(Configuration)} method.
 * <p>
 * These operations are expected to be invoked within a {@link org.axonframework.configuration.DecoratorDefinition}. As
 * such, <b>any</b> registered interceptors are <b>only</b> applied when the infrastructure component requiring them is
 * constructed. When, for example, an {@link org.axonframework.commandhandling.InterceptingCommandBus} is constructed,
 * this registry is invoked to retrieve interceptors. Interceptors that are registered once the
 * {@code InterceptingCommandBus} has already been constructed are not taken into ac
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public interface DispatchInterceptorRegistry {

    /**
     * Registers the given {@code interceptorBuilder} for a generic {@link Message} {@link MessageDispatchInterceptor}.
     *
     * @param interceptorBuilder The generic {@link Message} {@link MessageDispatchInterceptor} builder to register.
     * @return This {@code InterceptorRegistry}, for fluent interfacing.
     */
    @Nonnull
    DispatchInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super Message>> interceptorBuilder
    );

    /**
     * Returns the list of {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry.
     *
     * @param config The configuration to build all {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     *               with.
     * @return The list of {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry.
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super Message>> interceptors(@Nonnull Configuration config);
}
