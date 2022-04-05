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

import org.axonframework.messaging.Message;

import java.lang.reflect.Executable;
import java.util.Optional;
import javax.annotation.Nonnull;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * The default HandlerDefinition implementation in Axon. It implements the rules of annotated handlers used
 * in all the different types of handlers in Axon.
 * <p>
 * For this implementation to recognize a handler method, it should be (meta)annotated with {@link MessageHandler}. It
 * is recommended to meta-annotated members, and preconfigure the expected {@code messageType}. For example, and event
 * handler should define {@code @MessageHandler(messageType = EventMessage.class)}, indicating that this handler should
 * only be invoked for {@link org.axonframework.eventhandling.EventMessage}s.
 * <p>
 * Use {@link HandlerEnhancerDefinition} to add extra behavior or information on top of handlers created by this
 * definition.
 *
 * @see HandlerEnhancerDefinition
 * @see org.axonframework.commandhandling.CommandHandler
 * @see org.axonframework.eventhandling.EventHandler
 */
public class AnnotatedMessageHandlingMemberDefinition implements HandlerDefinition {

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<MessageHandlingMember<T>> createHandler(@Nonnull Class<T> declaringType,
                                                                @Nonnull Executable executable,
                                                                @Nonnull ParameterResolverFactory parameterResolverFactory) {
        return findAnnotationAttributes(executable, MessageHandler.class)
                .map(attr -> new AnnotatedMessageHandlingMember<>(
                        executable,
                        (Class<? extends Message<?>>) attr.getOrDefault("messageType", Message.class),
                        (Class<? extends Message<?>>) attr.getOrDefault("payloadType", Object.class),
                        parameterResolverFactory));
    }
}
