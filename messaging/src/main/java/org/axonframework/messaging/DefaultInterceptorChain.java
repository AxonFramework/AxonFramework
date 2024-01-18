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

import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Iterator;

/**
 * Mechanism that takes care of interceptor and handler execution.
 *
 * @param <T> The message type this interceptor chain can process
 * @author Allard Buijze
 * @since 0.5
 */
public class DefaultInterceptorChain<T extends Message<?>, R> implements InterceptorChain {

    private final MessageHandler<? super T, ? extends R> handler;
    private final Iterator<? extends MessageHandlerInterceptor<? super T>> chain;
    private final UnitOfWork<? extends T> unitOfWork;

    /**
     * Initialize the default interceptor chain to dispatch the given {@code message}, through the
     * {@code chain}, to the {@code handler}.
     *
     * @param unitOfWork    The UnitOfWork the message is executed in
     * @param interceptors  The interceptors composing the chain
     * @param handler       The handler for the message
     */
    public DefaultInterceptorChain(UnitOfWork<? extends T> unitOfWork,
                                   Iterable<? extends MessageHandlerInterceptor<? super T>> interceptors,
                                   MessageHandler<? super T, ? extends R> handler) {
        this.handler = handler;
        this.chain = interceptors.iterator();
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Object proceed() throws Exception {
        if (chain.hasNext()) {
            return chain.next().handle(unitOfWork, this);
        } else {
            return handler.handleSync(unitOfWork.getMessage());
        }
    }
}
