/*
 * Copyright (c) 2010-2022. Axon Framework
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
 * Interface marking components capable of registering Dispatch Interceptors. Generally, these are Messaging components
 * injected into the sending end of the communication.
 * <p>
 * Dispatch Interceptors are always invoked in the thread that dispatches the message to the messaging component. If a
 * Unit of Work is active, it is not that of the dispatched message, but of the message that triggered this message to
 * be published.
 *
 * @param <T> The type of Message the interceptor works with
 * @see MessageHandlerInterceptor
 */
public interface MessageDispatchInterceptorSupport<T extends Message<?>> {

    /**
     * Register the given DispatchInterceptor. After registration, the interceptor will be invoked for each Message
     * dispatched on the messaging component that it was registered to.
     *
     * @param dispatchInterceptor The interceptor to register
     * @return A Registration, which may be used to deregister the interceptor.
     */
    Registration registerDispatchInterceptor(@Nonnull MessageDispatchInterceptor<? super T> dispatchInterceptor);
}
