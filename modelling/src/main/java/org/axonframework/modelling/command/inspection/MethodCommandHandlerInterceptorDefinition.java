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
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Implementation of {@link HandlerEnhancerDefinition} used for {@link CommandHandlerInterceptor} annotated methods.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class MethodCommandHandlerInterceptorDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.annotationAttributes(CommandHandlerInterceptor.class)
                       .map(attr -> (MessageHandlingMember<T>) new MethodCommandHandlerInterceptorHandlingMember<>(
                               original, attr))
                       .orElse(original);
    }

    private static class MethodCommandHandlerInterceptorHandlingMember<T> extends WrappedMessageHandlingMember<T>
            implements CommandHandlerInterceptorHandlingMember<T> {

        private final Pattern commandNamePattern;
        private final boolean shouldInvokeInterceptorChain;

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        private MethodCommandHandlerInterceptorHandlingMember(MessageHandlingMember<T> delegate,
                                                              Map<String, Object> annotationAttributes) {
            super(delegate);
            Method method = delegate.unwrap(Method.class).orElseThrow(() -> new AxonConfigurationException(
                    "The @CommandHandlerInterceptor must be on method."));
            shouldInvokeInterceptorChain = Arrays.stream(method.getParameters())
                                                 .noneMatch(p -> p.getType().equals(InterceptorChain.class));
            if (shouldInvokeInterceptorChain && !Void.TYPE.equals(method.getReturnType())) {
                throw new AxonConfigurationException("@CommandHandlerInterceptor must return void or declare " +
                                                             "InterceptorChain parameter.");
            }
            commandNamePattern = Pattern.compile((String) annotationAttributes.get("commandNamePattern"));
        }

        @Override
        public boolean shouldInvokeInterceptorChain() {
            return shouldInvokeInterceptorChain;
        }

        @Override
        public boolean canHandle(Message<?> message) {
            return super.canHandle(message) && commandNamePattern.matcher(((CommandMessage) message).getCommandName())
                                                                 .matches();
        }
    }
}
