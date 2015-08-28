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

package org.axonframework.commandhandling;

import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * Workflow interface that allows for customized command handler invocation chains. A CommandHandlerInterceptor can add
 * customized behavior to command handler invocations, both before and after the invocation.
 * <p/>
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface CommandHandlerInterceptor {

    /**
     * The handle method is invoked each time a command is dispatched through the command bus that the
     * CommandHandlerInterceptor is declared on. The incoming command and contextual information can be found in the
     * given <code>unitOfWork</code>.
     * <p/>
     * The interceptor is responsible for the continuation of the dispatch process by invoking the {@link
     * org.axonframework.commandhandling.InterceptorChain#proceed(CommandMessage)} method on the given
     * <code>interceptorChain</code>.
     * <p/>
     * Any information gathered by interceptors may be attached to the unitOfWork. This information is made
     * available to the CommandCallback provided by the dispatching component.
     * <p/>
     * Interceptors are highly recommended not to change the type of the command handling result, as the dispatching
     * component might expect a result of a specific type.
     *
     * @param commandMessage   The command being dispatched
     * @param unitOfWork       The UnitOfWork in which
     * @param interceptorChain The interceptor chain that allows this interceptor to proceed the dispatch process
     * @return the result of the command handler. May have been modified by interceptors.
     *
     * @throws Throwable any exception that occurs while handling the command
     */
    Object handle(CommandMessage<?> commandMessage, UnitOfWork unitOfWork, InterceptorChain interceptorChain)
            throws Throwable;
}
