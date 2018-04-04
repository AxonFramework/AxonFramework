/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Sara Pellegrini on 29/03/2018.
 * sara.pellegrini@gmail.com
 */
public class GenericDispatchInterceptors<M extends Message<?>> implements DispatchInterceptors<M> {

    private final List<MessageDispatchInterceptor<? super M>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super M> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public <T extends M> T intercept(T message) {
        T messageToDispatch = message;
        for (MessageDispatchInterceptor<? super M> interceptor : dispatchInterceptors) {
            messageToDispatch = (T) interceptor.handle(messageToDispatch);
        }
        return messageToDispatch;
    }
}
