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

package org.axonframework.eventhandling.annotation;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import jakarta.annotation.Nonnull;

/**
 * Definition of handlers that can handle {@link EventMessage}s. These handlers are wrapped with an
 * {@link EventHandlingMember} that exposes event-specific handler information.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MethodEventMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.unwrap(Method.class)
                       .filter(method -> method.isAnnotationPresent(EventHandler.class))
                       .map(method -> (MessageHandlingMember<T>)
                               new MethodEventMessageHandlerDefinition.MethodEventMessageHandlingMember<>(original)
                       )
                       .orElse(original);
    }

    private static class MethodEventMessageHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements EventHandlingMember<T> {

        public MethodEventMessageHandlingMember(MessageHandlingMember<T> original) {
            super(original);

            if (original.unwrap(Method.class).isEmpty()) {
                throw new UnsupportedHandlerException(
                        "@EventHandler annotation can only be put on methods.",
                        original.unwrap(Member.class).orElse(null)
                );
            }
        }

        @Override
        public boolean canHandle(@Nonnull Message message, @Nonnull ProcessingContext context) {
            return super.canHandle(message, context) && message instanceof EventMessage;
        }
    }
}