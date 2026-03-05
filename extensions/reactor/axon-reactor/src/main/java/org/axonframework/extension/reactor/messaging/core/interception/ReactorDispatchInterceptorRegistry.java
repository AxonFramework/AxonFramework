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

package org.axonframework.extension.reactor.messaging.core.interception;

import org.jspecify.annotations.NonNull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;

import java.util.List;

/**
 * A registry of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} for the Reactor extension.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, and {@link QueryMessage}-specific {@code ReactorMessageDispatchInterceptors}.
 * Registered interceptors can be retrieved through {@link #commandInterceptors()}, {@link #eventInterceptors()}, and
 * {@link #queryInterceptors()}.
 * <p>
 * Generic {@link Message} interceptors registered via {@link #registerInterceptor(ReactorMessageDispatchInterceptor)}
 * are automatically applied to all message types.
 * <p>
 * This registry follows the same pattern as
 * {@link org.axonframework.messaging.core.interception.DispatchInterceptorRegistry DispatchInterceptorRegistry} but is
 * specific to reactor-based interceptors.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactorMessageDispatchInterceptor
 */
@Internal
public interface ReactorDispatchInterceptorRegistry {

    /**
     * Registers a generic {@link ReactorMessageDispatchInterceptor} for all message types.
     * <p>
     * The interceptor will be applied to {@link CommandMessage}s, {@link EventMessage}s, and {@link QueryMessage}s.
     *
     * @param interceptor the generic dispatch interceptor to register
     * @return this registry, for fluent interfacing
     */
    @NonNull
    ReactorDispatchInterceptorRegistry registerInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<Message> interceptor
    );

    /**
     * Registers a {@link ReactorMessageDispatchInterceptor} for {@link CommandMessage}s.
     *
     * @param interceptor the command dispatch interceptor to register
     * @return this registry, for fluent interfacing
     */
    @NonNull
    ReactorDispatchInterceptorRegistry registerCommandInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<? super CommandMessage> interceptor
    );

    /**
     * Registers a {@link ReactorMessageDispatchInterceptor} for {@link EventMessage}s.
     *
     * @param interceptor the event dispatch interceptor to register
     * @return this registry, for fluent interfacing
     */
    @NonNull
    ReactorDispatchInterceptorRegistry registerEventInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<? super EventMessage> interceptor
    );

    /**
     * Registers a {@link ReactorMessageDispatchInterceptor} for {@link QueryMessage}s.
     *
     * @param interceptor the query dispatch interceptor to register
     * @return this registry, for fluent interfacing
     */
    @NonNull
    ReactorDispatchInterceptorRegistry registerQueryInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<? super QueryMessage> interceptor
    );

    /**
     * Returns the list of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} registered for
     * {@link CommandMessage}s.
     *
     * @return the list of command dispatch interceptors
     */
    @NonNull
    List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors();

    /**
     * Returns the list of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} registered for
     * {@link EventMessage}s.
     *
     * @return the list of event dispatch interceptors
     */
    @NonNull
    List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors();

    /**
     * Returns the list of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} registered for
     * {@link QueryMessage}s.
     *
     * @return the list of query dispatch interceptors
     */
    @NonNull
    List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors();
}
