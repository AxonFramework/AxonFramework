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

package org.axonframework.axonserver.connector;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Composite of {@link MessageDispatchInterceptor}s that apply all interceptors in the order of registration.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class DispatchInterceptors<M extends Message<?>> {

    private final List<MessageDispatchInterceptor<? super M>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super M> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @SuppressWarnings("unchecked")
    public <T extends M> T intercept(T message) {
        T messageToDispatch = message;
        for (MessageDispatchInterceptor<? super M> interceptor : dispatchInterceptors) {
            messageToDispatch = (T) interceptor.handle(messageToDispatch);
        }
        return messageToDispatch;
    }
}
