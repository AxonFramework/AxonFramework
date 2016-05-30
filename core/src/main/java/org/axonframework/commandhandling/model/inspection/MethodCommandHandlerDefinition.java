/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.AbstractMessageHandler;
import org.axonframework.common.annotation.HandlerDefinition;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.Message;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Optional;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

public class MethodCommandHandlerDefinition implements HandlerDefinition {

    @Override
    public <T> Optional<MessageHandler<T>> createHandler(Class<T> declaringType, Executable executable, ParameterResolverFactory parameterResolverFactory) {
        Map<String, Object> annotation = findAnnotationAttributes(executable, CommandHandler.class).orElse(null);
        if (annotation != null && executable.getParameterCount() > 0) {
            return Optional.of(new MethodCommandMessageHandler<>(executable, annotation,
                                                                 parameterResolverFactory));
        }
        return Optional.empty();
    }

    private class MethodCommandMessageHandler<T> extends AbstractMessageHandler<T> implements CommandMessageHandler<T> {

        private final String commandName;
        private final boolean isFactoryHandler;
        private final String routingKey;

        public MethodCommandMessageHandler(Executable executable, Map<String, Object> annotationAttributes,
                                           ParameterResolverFactory parameterResolverFactory) {
            super(executable,
                  (Class<?>) annotationAttributes.getOrDefault("payloadType", Object.class),
                  parameterResolverFactory);
            this.routingKey = "".equals(annotationAttributes.get("routingKey")) ? null : (String) annotationAttributes.get("routingKey");
            if ("".equals(annotationAttributes.get("commandName"))) {
                commandName = executable.getParameters()[0].getType().getName();
            } else {
                commandName = (String) annotationAttributes.get("commandName");
            }
            isFactoryHandler = (executable instanceof Constructor);
        }

        @Override
        protected boolean typeMatches(Message<?> message) {
            return message instanceof CommandMessage && commandName.equals(((CommandMessage) message).getCommandName());
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
