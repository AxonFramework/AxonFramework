/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.common.Priority;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ResourceOverridingProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Parameter resolver factory that adds support for resolving current {@link InterceptorChain}. This can function only
 * if there is an active {@link org.axonframework.messaging.unitofwork.UnitOfWork}.
 *
 * @author Milan Savic
 * @since 3.3
 */
@Priority(Priority.FIRST)
public class InterceptorChainParameterResolverFactory
        implements ParameterResolverFactory, ParameterResolver<InterceptorChain> {

    private static final ThreadLocal<InterceptorChain<?, ?>> CURRENT = new ThreadLocal<>();
    private static final ResourceKey<InterceptorChain<?, ?>> INTERCEPTOR_CHAIN_KEY =
            ResourceKey.sharedKey("InterceptorChain");

    /**
     * Invoke the given {@code action} with the given {@code interceptorChain} being available for parameter injection.
     * Because this parameter is not bound to a message, it is important to invoke handlers using this method.
     *
     * @param interceptorChain The InterceptorChain to consider for injection as parameter
     * @param action           The action to invoke
     * @param <R>              The type of response expected from the invocation
     * @return The response from the invocation of given {@code action}
     * @throws Exception any exception that occurs while invoking given {@code action}
     */
    public static <R> R callWithInterceptorChainSync(InterceptorChain interceptorChain,
                                                     Callable<R> action) throws Exception {
        InterceptorChain previous = CURRENT.get();
        CURRENT.set(interceptorChain);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Invoke the given {@code action} with the given {@code interceptorChain} being available for parameter injection.
     * Because this parameter is not bound to a message, it is important to invoke handlers using this method.
     *
     * @param interceptorChain The InterceptorChain to consider for injection as parameter
     * @param action           The action to invoke
     * @return The response from the invocation of given {@code action}
     */
    public static <M extends Message<?>, T extends Message<?>> MessageStream<? extends T> callWithInterceptorChain(
            ProcessingContext processingContext,
            InterceptorChain<M, T> interceptorChain,
            Function<ProcessingContext, MessageStream<? extends T>> action
    ) {
        ProcessingContext newProcessingContext = new ResourceOverridingProcessingContext<>(processingContext,
                                                                                           INTERCEPTOR_CHAIN_KEY,
                                                                                           interceptorChain);
        return action.apply(newProcessingContext);
    }

    /**
     * Returns the current interceptor chain registered for injection as a parameter. Will return the instance passed in
     * {@link #callWithInterceptorChainSync(InterceptorChain, Callable)}. When invoked outside the scope of that method,
     * this will return {@code null}.
     *
     * @return the InterceptorChain instance passed in {@link #callWithInterceptorChainSync(InterceptorChain, Callable)}
     */
    public static InterceptorChain currentInterceptorChain() {
        return CURRENT.get();
    }

    public static <M extends Message<?>, R extends Message<?>> InterceptorChain<M, R> currentInterceptorChain(
            ProcessingContext processingContext
    ) {
        //noinspection unchecked
        return (InterceptorChain<M, R>) processingContext.getResource(INTERCEPTOR_CHAIN_KEY);
    }

    @Override
    public InterceptorChain resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
        InterceptorChain interceptorChain = processingContext == null
                ? null
                : processingContext.getResource(INTERCEPTOR_CHAIN_KEY);
        if (interceptorChain == null) {
            interceptorChain = CURRENT.get();
        }
        if (interceptorChain != null) {
            return interceptorChain;
        }
        throw new IllegalStateException("InterceptorChain should have been injected");
    }

    @Override
    public boolean matches(Message<?> message, ProcessingContext processingContext) {
        return CURRENT.get() != null
                || (processingContext != null && processingContext.containsResource(INTERCEPTOR_CHAIN_KEY));
    }

    @Override
    public ParameterResolver<InterceptorChain> createInstance(Executable executable,
                                                              Parameter[] parameters,
                                                              int parameterIndex) {
        if (InterceptorChain.class.equals(parameters[parameterIndex].getType())) {
            return this;
        }
        return null;
    }
}
