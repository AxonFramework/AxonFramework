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

package org.axonframework.commandhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Implementation of a {@link HandlerEnhancerDefinition} that is used for {@link CommandHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class MethodCommandHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.annotationAttributes(CommandHandler.class)
                       .map(attr -> (MessageHandlingMember<T>) new MethodCommandMessageHandlingMember<>(original, attr))
                       .orElse(original);
    }

    private static class MethodCommandMessageHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements CommandMessageHandlingMember<T> {

        private final String commandName;
        private final boolean isFactoryHandler;
        private final String routingKey;

        private MethodCommandMessageHandlingMember(MessageHandlingMember<T> delegate,
                                                   Map<String, Object> annotationAttributes) {
            super(delegate);
            this.routingKey = "".equals(annotationAttributes.get("routingKey")) ? null :
                    (String) annotationAttributes.get("routingKey");
            Executable executable = delegate.unwrap(Executable.class).orElseThrow(() -> new AxonConfigurationException(
                    "The @CommandHandler annotation must be put on an Executable (either directly or as Meta " +
                            "Annotation)"));
            if ("".equals(annotationAttributes.get("commandName"))) {
                commandName = delegate.payloadType().getName();
            } else {
                commandName = (String) annotationAttributes.get("commandName");
            }
            final boolean factoryMethod = executable instanceof Method && Modifier.isStatic(executable.getModifiers());
            if (factoryMethod && !executable.getDeclaringClass()
                                            .isAssignableFrom(((Method) executable).getReturnType())) {
                throw new AxonConfigurationException("static @CommandHandler methods must declare a return value " +
                                                             "which is equal to or a subclass of the declaring type");
            }
            isFactoryHandler = executable instanceof Constructor || factoryMethod;
        }

        @Override
        public boolean canHandle(@Nonnull Message<?> message) {
            return super.canHandle(message) && commandName.equals(((CommandMessage<?>) message).getCommandName());
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
