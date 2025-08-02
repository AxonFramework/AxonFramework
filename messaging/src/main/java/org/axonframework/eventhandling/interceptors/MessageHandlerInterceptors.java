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

package org.axonframework.eventhandling.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A collection of {@link MessageHandlerInterceptor}s that can be registered with an {@link EventProcessor}. It allows
 * for the registration and retrieval of interceptors.
 * <p>
 * This class is thread-safe and supports concurrent modifications.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class MessageHandlerInterceptors {

    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();

    public MessageHandlerInterceptors() {
    }

    public MessageHandlerInterceptors(@Nonnull List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors) {
        this.interceptors.addAll(Objects.requireNonNull(interceptors, "interceptors may not be null"));
    }

    /**
     * Registers a {@link MessageHandlerInterceptor} for the event processor. The interceptor will be applied to all
     * messages handled by this event processor.
     *
     * @param interceptor The interceptor to register.
     * @return A {@link Registration} that can be used to unregister the interceptor.
     */
    public Registration register(
            @Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    /**
     * Return the list of already registered {@link MessageHandlerInterceptor}s for the event processor. To register a
     * new interceptor use {@link MessageHandlerInterceptors#register(MessageHandlerInterceptor)}
     *
     * @return The list of registered interceptors of the event processor.
     */
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> toList() {
        return Collections.unmodifiableList(interceptors);
    }
}
