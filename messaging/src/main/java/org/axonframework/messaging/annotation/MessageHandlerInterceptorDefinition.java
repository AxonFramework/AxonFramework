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

package org.axonframework.messaging.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReversedOrder;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.ResultHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@link HandlerEnhancerDefinition} that marks methods (meta-)annotated with {@link MessageHandlerInterceptor}
 * as interceptors. These methods need to be given special treatment when invoking handlers. Matching
 * interceptors need to be invoked first, allowing them to proceed the invocation chain.
 * <p>
 * This definition also recognizes interceptors only acting on the response. These must be meta-annotated with
 * {@link ResultHandler}.
 *
 * @author Allard Buijze
 * @since 4.4
 */
public class MessageHandlerInterceptorDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull
    <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {

        if (original.annotationAttributes(MessageHandlerInterceptor.class).isPresent()) {
            Optional<Map<String, Object>> attributes = original.annotationAttributes(ResultHandler.class);
            if (attributes.isPresent()) {
                return new ResultHandlingInterceptorMember<>(original, (Class<?>) attributes.get().get("resultType"));
            }
            return new InterceptedMessageHandlingMember<>(original);
        }
        return original;
    }

    private static class ResultHandlingInterceptorMember<T>
            extends WrappedMessageHandlingMember<T>
            implements MessageInterceptingMember<T>, ReversedOrder {

        private final Class<?> expectedResultType;

        public ResultHandlingInterceptorMember(MessageHandlingMember<T> original, Class<?> expectedResultType) {
            super(original);
            this.expectedResultType = expectedResultType;
            Method method = original.unwrap(Method.class).orElseThrow(() -> new AxonConfigurationException(
                    "Only methods can be marked as MessageHandlerInterceptor. Violating handler: " + original.signature()));
            boolean declaredInterceptorChain = Arrays.stream(method.getParameters())
                                                     .anyMatch(p -> p.getType().equals(InterceptorChain.class));
            if (declaredInterceptorChain) {
                throw new AxonConfigurationException("A MessageHandlerInterceptor acting on the invocation result must not declare a parameter of type InterceptorChain. Violating handler: " + original.signature());
            }
        }

        @Override
        public int priority() {
            return Integer.MIN_VALUE;
        }

        @Override
        public boolean canHandle(@Nonnull Message<?> message) {
            return ResultParameterResolverFactory.ignoringResultParameters(() -> super.canHandle(message));
        }

        @Override
        public Object handle(@Nonnull Message<?> message, @Nullable T target) throws Exception {
            InterceptorChain chain = InterceptorChainParameterResolverFactory.currentInterceptorChain();
            try {
                return chain.proceed();
            } catch (Exception e) {
                if (!expectedResultType.isInstance(e)) {
                    throw e;
                }
                return ResultParameterResolverFactory.callWithResult(e, () -> {
                    if (super.canHandle(message)) {
                        return super.handle(message, target);
                    }
                    throw e;
                });
            }
        }
    }

    private static class InterceptedMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> implements MessageInterceptingMember<T> {

        private final boolean shouldInvokeInterceptorChain;

        public InterceptedMessageHandlingMember(MessageHandlingMember<T> original) {
            super(original);
            Method method = original.unwrap(Method.class).orElseThrow(() -> new AxonConfigurationException(
                    "Only methods can be marked as MessageHandlerInterceptor. Violating handler: " + original.signature()));
            shouldInvokeInterceptorChain = Arrays.stream(method.getParameters())
                                                 .noneMatch(p -> p.getType().equals(InterceptorChain.class));
            if (shouldInvokeInterceptorChain && !Void.TYPE.equals(method.getReturnType())) {
                throw new AxonConfigurationException("A MessageHandlerInterceptor must either return null or declare a parameter of type InterceptorChain. Violating handler: " + original.signature());
            }
        }

        @Override
        public Object handle(@Nonnull Message<?> message, @Nullable T target) throws Exception {
            Object result = super.handle(message, target);
            if (shouldInvokeInterceptorChain) {
                return InterceptorChainParameterResolverFactory.currentInterceptorChain().proceed();
            }
            return result;
        }


    }
}
