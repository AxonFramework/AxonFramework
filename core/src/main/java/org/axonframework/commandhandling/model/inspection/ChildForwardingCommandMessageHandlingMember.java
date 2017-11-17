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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Implementation of a {@link CommandMessageHandlingMember} that forwards commands to a child entity.
 *
 * @param <P> the parent entity type
 * @param <C> the child entity type
 */
public class ChildForwardingCommandMessageHandlingMember<P, C> implements CommandMessageHandlingMember<P> {

    private final MessageHandlingMember<? super C> childHandler;
    private final BiFunction<CommandMessage<?>, P, C> childEntityResolver;
    private final String commandName;
    private final boolean isFactoryHandler;

    /**
     * Initializes a {@link ChildForwardingCommandMessageHandlingMember} that routes commands to a compatible child
     * entity. Child entities are resolved using the given {@code childEntityResolver}. If an entity is found the
     * command will be handled using the given {@code childHandler}.
     *
     * @param childHandler        handler of the command once a suitable entity is found
     * @param childEntityResolver resolver of child entities for a given command
     */
    public ChildForwardingCommandMessageHandlingMember(MessageHandlingMember<? super C> childHandler,
                                                       BiFunction<CommandMessage<?>, P, C> childEntityResolver) {
        this.childHandler = childHandler;
        this.childEntityResolver = childEntityResolver;
        this.commandName =
                childHandler.unwrap(CommandMessageHandlingMember.class).map(CommandMessageHandlingMember::commandName)
                        .orElse(null);
        this.isFactoryHandler = childHandler.unwrap(CommandMessageHandlingMember.class)
                .map(CommandMessageHandlingMember::isFactoryHandler).orElse(false);
    }

    @Override
    public String commandName() {
        return commandName;
    }

    @Override
    public String routingKey() {
        return null;
    }

    @Override
    public boolean isFactoryHandler() {
        return isFactoryHandler;
    }

    @Override
    public Class<?> payloadType() {
        return childHandler.payloadType();
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean canHandle(Message<?> message) {
        return childHandler.canHandle(message);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object handle(Message<?> message, P target) throws Exception {
        C childEntity = childEntityResolver.apply((CommandMessage<?>) message, target);
        if (childEntity == null) {
            throw new IllegalStateException(
                    "Aggregate cannot handle this command, as there is no entity instance to forward it to.");
        }
        return childHandler.handle(message, childEntity);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
        if (handlerType.isInstance(this)) {
            return (Optional<HT>) Optional.of(this);
        }
        return childHandler.unwrap(handlerType);
    }

    @Override
    public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
        return childHandler.annotationAttributes(annotationType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return childHandler.hasAnnotation(annotationType);
    }
}
