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

import org.axonframework.commandhandling.model.AbstractMessageHandler;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.annotation.HandlerDefinition;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;

import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Optional;

public class MethodEventHandlerDefinition implements HandlerDefinition {

    @Override
    public <T> Optional<MessageHandler<T>> createHandler(Class<T> declaringType, Executable executable, ParameterResolverFactory parameterResolverFactory) {
        Map<String, Object> annotation = AnnotationUtils.findAnnotationAttributes(executable, EventHandler.class).orElse(null);
        if (annotation != null && executable.getParameterCount() > 0) {
            Class<?> explicitPayloadType = (Class<?>) annotation.get("eventType");
            return Optional.of(new MethodMessageHandler<>(executable, explicitPayloadType, parameterResolverFactory));
        }
        return Optional.empty();
    }

    private class MethodMessageHandler<T> extends AbstractMessageHandler<T> {

        private final Class<?> expectedPayloadType;

        public MethodMessageHandler(Executable executable, Class<?> expectedPayloadType,
                                    ParameterResolverFactory parameterResolverFactory) {
            super(executable, expectedPayloadType, parameterResolverFactory);
            this.expectedPayloadType = expectedPayloadType;
        }

        @Override
        protected boolean typeMatches(Message<?> message) {
            return EventMessage.class.isInstance(message)
                    && expectedPayloadType.isAssignableFrom(message.getPayloadType());
        }

    }
}
