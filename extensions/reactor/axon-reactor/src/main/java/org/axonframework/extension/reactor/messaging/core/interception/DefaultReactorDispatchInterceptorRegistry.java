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
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptorChain;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of the {@link ReactorDispatchInterceptorRegistry}, maintaining lists of
 * {@link CommandMessage}, {@link EventMessage}, and {@link QueryMessage}-specific
 * {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors}.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
public class DefaultReactorDispatchInterceptorRegistry implements ReactorDispatchInterceptorRegistry {

    private final List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = new ArrayList<>();
    private final List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = new ArrayList<>();
    private final List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = new ArrayList<>();

    @NonNull
    @Override
    public ReactorDispatchInterceptorRegistry registerInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<Message> interceptor
    ) {
        Objects.requireNonNull(interceptor, "Interceptor may not be null");
        registerCommandInterceptor(
                (message, context, chain) -> interceptor.interceptOnDispatch(
                        message, context, (m, c) -> chain.proceed((CommandMessage) m, c)
                )
        );
        registerEventInterceptor(
                (message, context, chain) -> interceptor.interceptOnDispatch(
                        message, context, (m, c) -> chain.proceed((EventMessage) m, c)
                )
        );
        registerQueryInterceptor(
                (message, context, chain) -> interceptor.interceptOnDispatch(
                        message, context, (m, c) -> chain.proceed((QueryMessage) m, c)
                )
        );
        return this;
    }

    @NonNull
    @Override
    public ReactorDispatchInterceptorRegistry registerCommandInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<? super CommandMessage> interceptor
    ) {
        commandInterceptors.add(Objects.requireNonNull(interceptor, "Interceptor may not be null"));
        return this;
    }

    @NonNull
    @Override
    public ReactorDispatchInterceptorRegistry registerEventInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<? super EventMessage> interceptor
    ) {
        eventInterceptors.add(Objects.requireNonNull(interceptor, "Interceptor may not be null"));
        return this;
    }

    @NonNull
    @Override
    public ReactorDispatchInterceptorRegistry registerQueryInterceptor(
            @NonNull ReactorMessageDispatchInterceptor<? super QueryMessage> interceptor
    ) {
        queryInterceptors.add(Objects.requireNonNull(interceptor, "Interceptor may not be null"));
        return this;
    }

    @NonNull
    @Override
    public List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors() {
        return List.copyOf(commandInterceptors);
    }

    @NonNull
    @Override
    public List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors() {
        return List.copyOf(eventInterceptors);
    }

    @NonNull
    @Override
    public List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors() {
        return List.copyOf(queryInterceptors);
    }
}
