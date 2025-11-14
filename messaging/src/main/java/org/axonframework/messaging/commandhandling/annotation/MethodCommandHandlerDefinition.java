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

package org.axonframework.messaging.commandhandling.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.AbstractAnnotatedMessageHandlerEnhancerDefinition;
import org.axonframework.messaging.core.annotation.HandlerAttributes;
import org.axonframework.messaging.core.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.WrappedMessageHandlingMember;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Implementation of a {@link HandlerEnhancerDefinition} that is used for {@link CommandHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 3.0.0
 */
public class MethodCommandHandlerDefinition extends AbstractAnnotatedMessageHandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        String routingKey = original.<String>attribute(HandlerAttributes.COMMAND_ROUTING_KEY)
                                    .filter(s -> !s.isEmpty())
                                    .orElse(null);
        return resolveMessageNameFromPayloadType(original, HandlerAttributes.COMMAND_NAME, CommandMessage.class)
                .<MessageHandlingMember<T>>map(name -> new MethodCommandHandlingMember<>(original, routingKey, name))
                .orElse(original);
    }

    private static class MethodCommandHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements CommandHandlingMember<T> {

        private final String commandName;
        private final boolean isFactoryHandler;
        private final String routingKey;

        private MethodCommandHandlingMember(MessageHandlingMember<T> delegate,
                                            String routingKeyAttribute,
                                            String commandNameAttribute) {
            super(delegate);
            Executable executable =
                    delegate.unwrap(Executable.class)
                            .orElseThrow(() -> new AxonConfigurationException(
                                    "The @CommandHandler annotation must be put on an Executable "
                                            + "(either directly or as Meta Annotation)"
                            ));

            routingKey = "".equals(routingKeyAttribute) ? null : routingKeyAttribute;
            commandName = "".equals(commandNameAttribute) ? delegate.payloadType().getName() : commandNameAttribute;
            final boolean factoryMethod = executable instanceof Method && Modifier.isStatic(executable.getModifiers());
            isFactoryHandler = executable instanceof Constructor || factoryMethod;
        }

        @Override
        public boolean canHandle(@Nonnull Message message, @Nonnull ProcessingContext context) {
            return super.canHandle(message, context);
        }

        @Override
        public String routingKey() {
            return routingKey;
        }

        @Override
        public String commandName() {
            return commandName;
        }

        @Override
        public boolean isFactoryHandler() {
            return isFactoryHandler;
        }
    }
}
