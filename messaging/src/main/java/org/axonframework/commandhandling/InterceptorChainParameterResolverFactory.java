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

package org.axonframework.commandhandling;

import org.axonframework.common.Assert;
import org.axonframework.common.Priority;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * Parameter resolver factory that adds support for resolving current {@link InterceptorChain}. {@link
 * InterceptorChain}
 * can be initialized using static method {@link InterceptorChainParameterResolverFactory#initialize(InterceptorChain)}.
 * This can function only if there is an active {@link org.axonframework.messaging.unitofwork.UnitOfWork}.
 *
 * @author Milan Savic
 * @since 3.3
 */
@Priority(Priority.FIRST)
public class InterceptorChainParameterResolverFactory
        implements ParameterResolverFactory, ParameterResolver<InterceptorChain> {

    private static final String INTERCEPTOR_CHAIN_EMITTER_KEY = InterceptorChain.class.getName();

    /**
     * Initializes current unit of work with interceptor chain.
     *
     * @param interceptorChain the interceptor chain
     */
    public static void initialize(InterceptorChain interceptorChain) {
        Assert.state(CurrentUnitOfWork.isStarted(),
                     () -> "An active Unit of Work is required for injecting interceptor chain");
        CurrentUnitOfWork.get().resources().put(INTERCEPTOR_CHAIN_EMITTER_KEY, interceptorChain);
    }

    @Override
    public InterceptorChain resolveParameterValue(Message<?> message) {
        return CurrentUnitOfWork.map(uow -> (InterceptorChain) uow.getResource(INTERCEPTOR_CHAIN_EMITTER_KEY))
                                .orElseThrow(() -> new IllegalStateException(
                                        "InterceptorChain should have been injected"));
    }

    @Override
    public boolean matches(Message<?> message) {
        return message instanceof CommandMessage;
    }

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (InterceptorChain.class.equals(parameters[parameterIndex].getType())) {
            return this;
        }
        return null;
    }
}
