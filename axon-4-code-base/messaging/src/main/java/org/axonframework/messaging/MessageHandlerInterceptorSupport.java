/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.messaging;

import org.axonframework.common.Registration;

import javax.annotation.Nonnull;

/**
 * Interface marking components capable of registering Handler Interceptors. Generally, these are Messaging components
 * injected into the receiving end of the communication.
 * <p>
 * Handler Interceptors are always invoked in the thread that handles the message. If a Unit of Work is active, it is
 * that of the intercepted message.
 *
 * @param <T> The type of Message the interceptor works with
 * @see MessageDispatchInterceptor
 */
public interface MessageHandlerInterceptorSupport<T extends Message<?>> {

    /**
     * Register the given {@code handlerInterceptor}. After registration, the interceptor will be invoked for each
     * handled Message on the messaging component that it was registered to, prior to invoking the message's handler.
     *
     * @param handlerInterceptor The interceptor to register
     * @return A Registration, which may be used to deregister the interceptor.
     */
    Registration registerHandlerInterceptor(@Nonnull MessageHandlerInterceptor<? super T> handlerInterceptor);
}
