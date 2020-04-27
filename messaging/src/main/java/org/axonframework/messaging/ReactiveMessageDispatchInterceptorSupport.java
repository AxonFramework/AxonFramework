/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import org.axonframework.common.Registration;

/**
 * Interface marking components capable of registering a {@link ReactiveMessageDispatchInterceptor}. Generally, these are messaging
 * components injected into the sending end of the communication.
 *
 * @param <T>
 * @author Milan Savic
 * @since 4.4
 */
public interface ReactiveMessageDispatchInterceptorSupport<T extends Message<?>> {

    /**
     * Register the given {@link ReactiveMessageDispatchInterceptor}. After registration, the interceptor will be invoked for each
     * message dispatched on the messaging component that it was registered to.
     *
     * @param interceptor The reactive interceptor to register
     * @return a Registration, which may be used to unregister the interceptor
     */
    Registration registerDispatchInterceptor(ReactiveMessageDispatchInterceptor<T> interceptor);
}
