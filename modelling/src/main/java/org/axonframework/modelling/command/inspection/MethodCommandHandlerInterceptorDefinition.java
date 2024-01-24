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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.CommandHandlerInterceptor;

import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * Implementation of {@link HandlerEnhancerDefinition} used for {@link CommandHandlerInterceptor} annotated methods.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class MethodCommandHandlerInterceptorDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.<String>attribute(HandlerAttributes.COMMAND_NAME_PATTERN)
                       .map(commandNamePattern -> (MessageHandlingMember<T>)
                               new MethodCommandHandlerInterceptorHandlingMember<>(original, commandNamePattern)
                       )
                       .orElse(original);
    }

    private static class MethodCommandHandlerInterceptorHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        private final Pattern commandNamePattern;

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        private MethodCommandHandlerInterceptorHandlingMember(MessageHandlingMember<T> delegate,
                                                              String commandNamePattern) {
            super(delegate);
            this.commandNamePattern = Pattern.compile(commandNamePattern);
        }

        @Override
        public boolean canHandle(@Nonnull Message<?> message, ProcessingContext processingContext) {
            return super.canHandle(message, processingContext)
                    && commandNamePattern.matcher(((CommandMessage<?>) message).getCommandName())
                                         .matches();
        }
    }
}
