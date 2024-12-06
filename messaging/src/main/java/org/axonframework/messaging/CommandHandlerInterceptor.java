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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import javax.annotation.Nonnull;

/**
 * Workflow interface that allows for customized command handler invocation chains. A CommandHandlerInterceptor can add
 * customized behavior to command handler invocations, both before and after the invocation.
 *
 * @param <T> The command message type this interceptor can process
 * @author Christian Thiel
 * @since 4.11
 */
public interface CommandHandlerInterceptor<T extends CommandMessage<?>> extends MessageHandlerInterceptor<T> {

    /**
     * Invoked before a CommandMessage is handled by a designated {@link CommandHandler}.
     * <p/>
     * The interceptor is responsible for the continuation of the handling process by invoking the {@link
     * InterceptorChain#proceed()} method on the given {@code interceptorChain}.
     * <p/>
     * The given {@code unitOfWork} contains contextual information. Any information gathered by interceptors
     * may be attached to the unitOfWork.
     * <p/>
     * Interceptors are highly recommended not to change the type of the command message handling result, as the dispatching
     * component might expect a result of a specific type.
     *
     * @param unitOfWork       The UnitOfWork that is processing the command message
     * @param interceptorChain The interceptor chain that allows this interceptor to proceed the dispatch process
     * @return the result of the command handler. May have been modified by interceptors.
     *
     * @throws Exception any exception that occurs while handling the command message
     */
    Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception;
}
