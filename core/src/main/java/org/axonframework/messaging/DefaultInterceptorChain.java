/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Iterator;

/**
 * Mechanism that takes care of interceptor and handler execution.
 *
 * @param <T> The message type this interceptor chain can process
 * @author Allard Buijze
 * @since 0.5
 */
public class DefaultInterceptorChain<T extends Message<?>> implements InterceptorChain<T> {

    private final MessageHandler<? super T> handler;
    private final Iterator<? extends MessageHandlerInterceptor<T>> chain;
    private final UnitOfWork unitOfWork;
    private T message;

    /**
     * Initialize the default interceptor chain to dispatch the given <code>message</code>, through the
     * <code>chain</code>, to the <code>handler</code>.
     *
     * @param message       The message to dispatch through the interceptor chain
     * @param unitOfWork    The UnitOfWork the message is executed in
     * @param interceptors  The interceptors composing the chain
     * @param handler       The handler for the message
     */
    public DefaultInterceptorChain(T message, UnitOfWork unitOfWork,
                                   Iterable<? extends MessageHandlerInterceptor<T>> interceptors,
                                   MessageHandler<? super T> handler) {
        this.message = message;
        this.handler = handler;
        this.chain = interceptors.iterator();
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Object proceed(T messageToProceedWith) throws Exception {
        message = messageToProceedWith;
        if (chain.hasNext()) {
            return chain.next().handle(messageToProceedWith, unitOfWork, this);
        } else {
            return handler.handle(messageToProceedWith, unitOfWork);
        }
    }

    @Override
    public Object proceed() throws Exception {
        return proceed(message);
    }
}
