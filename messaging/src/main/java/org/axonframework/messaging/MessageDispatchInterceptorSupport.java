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

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;

/**
 * Interface marking components capable of registering Dispatch Interceptors. Generally, these are Messaging components
 * injected into the sending end of the communication.
 * <p>
 *
 * @param <M> The type of Message the interceptor works with
 * @see MessageDispatchInterceptor
 */
public interface MessageDispatchInterceptorSupport<M extends Message> {

    /**
     * Register the given DispatchInterceptor. After registration, the interceptor will be invoked for each Message
     * dispatched on the messaging component that it was registered to.
     *
     * @param dispatchInterceptor The interceptor to register
     * @return A Registration, which may be used to deregister the interceptor.
     */
    Registration registerDispatchInterceptor(@Nonnull MessageDispatchInterceptor<? super M> dispatchInterceptor);
}
