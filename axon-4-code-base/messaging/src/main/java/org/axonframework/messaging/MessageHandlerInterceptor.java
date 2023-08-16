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

import javax.annotation.Nonnull;

/**
 * Workflow interface that allows for customized message handler invocation chains. A MessageHandlerInterceptor can add
 * customized behavior to message handler invocations, both before and after the invocation.
 *
 * @param <T> The message type this interceptor can process
 * @author Allard Buijze
 * @since 0.5
 */
public interface MessageHandlerInterceptor<T extends Message<?>> {

    /**
     * Invoked before a Message is handled by a designated {@link org.axonframework.messaging.MessageHandler}.
     * <p/>
     * The interceptor is responsible for the continuation of the handling process by invoking the {@link
     * InterceptorChain#proceed()} method on the given {@code interceptorChain}.
     * <p/>
     * The given {@code unitOfWork} contains contextual information. Any information gathered by interceptors
     * may be attached to the unitOfWork.
     * <p/>
     * Interceptors are highly recommended not to change the type of the message handling result, as the dispatching
     * component might expect a result of a specific type.
     *
     * @param unitOfWork       The UnitOfWork that is processing the message
     * @param interceptorChain The interceptor chain that allows this interceptor to proceed the dispatch process
     * @return the result of the message handler. May have been modified by interceptors.
     *
     * @throws Exception any exception that occurs while handling the message
     */
    Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception;
}
