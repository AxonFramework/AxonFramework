/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.ResultHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * {@link HandlerEnhancerDefinition} that marks methods (meta-)annotated with {@link MessageHandlerInterceptor} as
 * interceptors. These methods need to be given special treatment when invoking handlers. Matching interceptors need to
 * be invoked first, allowing them to proceed the invocation chain.
 * <p>
 * This definition also recognizes interceptors only acting on the response. These must be meta-annotated with
 * {@link ResultHandler}.
 *
 * @author Allard Buijze
 * @since 4.4
 */
public class MessageHandlerInterceptorDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        String messageHandlerInterceptorMessageTypeAttributeKey =
                MessageHandlerInterceptor.class.getSimpleName() + ".messageType";
        if (original.attribute(messageHandlerInterceptorMessageTypeAttributeKey).isPresent()) {
            Optional<Class<?>> resultType = original.attribute(HandlerAttributes.RESULT_TYPE);
            return resultType.isPresent()
                    ? new ResultHandlingInterceptorMember<>(original, resultType.get())
                    : new InterceptedMessageHandlingMember<>(original);
        }
        return original;
    }

    private static class ResultHandlingInterceptorMember<T>
            extends WrappedMessageHandlingMember<T>
            implements MessageInterceptingMember<T> {

        private final Class<?> expectedResultType;

        public ResultHandlingInterceptorMember(MessageHandlingMember<T> original, Class<?> expectedResultType) {
            super(original);
            this.expectedResultType = expectedResultType;
            Method method = original.unwrap(Method.class).orElseThrow(() -> new AxonConfigurationException(
                    "Only methods can be marked as MessageHandlerInterceptor. "
                            + "Violating handler: " + original.signature())
            );
            boolean declaredInterceptorChain = Arrays.stream(method.getParameters())
                                                     .anyMatch(p -> p.getType().equals(InterceptorChain.class));
            if (declaredInterceptorChain) {
                throw new AxonConfigurationException(
                        "A MessageHandlerInterceptor acting on the invocation result must not "
                                + "declare a parameter of type InterceptorChain. "
                                + "Violating handler: " + original.signature()
                );
            }
        }

        @Override
        public int priority() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canHandle(@Nonnull Message message, @Nonnull ProcessingContext context) {
            return ResultParameterResolverFactory.ignoringResultParameters(context,
                                                                           pc -> super.canHandle(message, pc));
        }

        @Override
        public Object handleSync(@Nonnull Message message, @Nonnull ProcessingContext context, @Nullable T target)
                throws Exception {
            InterceptorChain chain = InterceptorChainParameterResolverFactory.currentInterceptorChain();
            try {
                return chain.proceedSync(context);
            } catch (Exception e) {
                if (!expectedResultType.isInstance(e)) {
                    throw e;
                }
                return ResultParameterResolverFactory.callWithResult(e, () -> {
                    if (super.canHandle(message, context)) {
                        return super.handleSync(message, context, target);
                    }
                    throw e;
                });
            }
        }

        @Override
        public MessageStream<?> handle(@Nonnull Message message,
                                       @Nonnull ProcessingContext context,
                                       @Nullable T target) {
            InterceptorChain<Message, Message> chain =
                    InterceptorChainParameterResolverFactory.currentInterceptorChain(context);
            // TODO - Provide implementation that handles exceptions in streams with more than one item
            return chain.proceed(message, context)
                        .onErrorContinue(error -> {
                            if (expectedResultType.isInstance(error)) {
                                return MessageStream.failed(error);
                            }
                            return ResultParameterResolverFactory.callWithResult(
                                    error,
                                    context,
                                    pc -> {
                                        if (super.canHandle(message, pc)) {
                                            //noinspection unchecked
                                            return super.handle(message, pc, target)
                                                        .map(r -> (Entry<Message>) r);
                                        }
                                        return MessageStream.failed(error);
                                    }
                            );
                        });
        }
    }

    private static class InterceptedMessageHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements MessageInterceptingMember<T> {

        private final boolean shouldInvokeInterceptorChain;

        public InterceptedMessageHandlingMember(MessageHandlingMember<T> original) {
            super(original);
            Method method = original.unwrap(Method.class).orElseThrow(() -> new AxonConfigurationException(
                    "Only methods can be marked as MessageHandlerInterceptor. "
                            + "Violating handler: " + original.signature())
            );
            shouldInvokeInterceptorChain = Arrays.stream(method.getParameters())
                                                 .noneMatch(p -> p.getType().equals(InterceptorChain.class));
            if (shouldInvokeInterceptorChain && !Void.TYPE.equals(method.getReturnType())) {
                throw new AxonConfigurationException(
                        "A MessageHandlerInterceptor must either return null or"
                                + " declare a parameter of type InterceptorChain. "
                                + "Violating handler: " + original.signature());
            }
        }

        @Override
        public Object handleSync(@Nonnull Message message, @Nonnull ProcessingContext context, @Nullable T target)
                throws Exception {
            Object result = super.handleSync(message, context, target);
            if (shouldInvokeInterceptorChain) {
                return InterceptorChainParameterResolverFactory.currentInterceptorChain().proceedSync(context);
            }
            return result;
        }
    }
}
