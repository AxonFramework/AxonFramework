/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.commandhandling.model.definitions;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.model.AbstractMessageHandler;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.Message;
import org.axonframework.commandhandling.model.inspection.CommandMessageHandler;
import org.axonframework.common.annotation.HandlerDefinition;
import org.axonframework.common.annotation.MessageHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.Optional;

import static org.axonframework.common.ReflectionUtils.findAnnotation;

public class MethodCommandHandlerDefinition implements HandlerDefinition {

    @Override
    public <T> Optional<MessageHandler<T>> createHandler(Class<T> declaringType, Executable executable, ParameterResolverFactory parameterResolverFactory) {
        CommandHandler annotation = findAnnotation(executable, CommandHandler.class);
        if (annotation != null && executable.getParameterCount() > 0) {
            return Optional.of(new MethodCommandMessageHandler<>(executable, annotation, parameterResolverFactory));
        }
        return Optional.empty();
    }

    private class MethodCommandMessageHandler<T> extends AbstractMessageHandler<T> implements CommandMessageHandler<T> {

        private final String commandName;
        private final boolean isFactoryHandler;

        public MethodCommandMessageHandler(Executable executable, CommandHandler annotation,
                                           ParameterResolverFactory parameterResolverFactory) {
            super(executable, parameterResolverFactory);
            if ("".equals(annotation.commandName())) {
                commandName = executable.getParameters()[0].getType().getName();
            } else {
                commandName = annotation.commandName();
            }
            isFactoryHandler = (executable instanceof Constructor);
        }

        @Override
        protected boolean typeMatches(Message<?> message) {
            return message instanceof CommandMessage && commandName.equals(((CommandMessage) message).getCommandName());
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
