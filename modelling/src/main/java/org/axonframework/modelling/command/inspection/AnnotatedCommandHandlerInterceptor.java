/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChainParameterResolverFactory;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * Annotated command handler interceptor on aggregate. Will invoke the delegate to the real interceptor method.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message
 * @author Milan Savic
 * @since 3.3
 */
public class AnnotatedCommandHandlerInterceptor<T> implements MessageHandlerInterceptor<CommandMessage<?>> {

    private final MessageHandlingMember<T> delegate;
    private final T target;

    /**
     * Initializes annotated command handler interceptor with delegate handler and target on which handler is to be
     * invoked.
     *
     * @param delegate delegate command handler interceptor
     * @param target   on which command handler interceptor is to be invoked
     */
    public AnnotatedCommandHandlerInterceptor(MessageHandlingMember<T> delegate, T target) {
        this.delegate = delegate;
        this.target = target;
    }

    @Override
    public Object handle(UnitOfWork<? extends CommandMessage<?>> unitOfWork, InterceptorChain interceptorChain)
            throws Exception {
        InterceptorChainParameterResolverFactory.initialize(interceptorChain);

        Object result = delegate.handle(unitOfWork.getMessage(), target);

        if (delegate.unwrap(CommandHandlerInterceptorHandlingMember.class)
                    .map(CommandHandlerInterceptorHandlingMember::shouldInvokeInterceptorChain).orElse(false)) {
            result = interceptorChain.proceed();
        }

        return result;
    }
}
