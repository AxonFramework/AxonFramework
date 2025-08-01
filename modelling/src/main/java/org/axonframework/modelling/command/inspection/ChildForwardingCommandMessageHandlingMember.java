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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.CommandMessageHandlingMember;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ChainedMessageHandlerInterceptorMember;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.NoMoreInterceptors;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.ChildEntityNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Implementation of a {@link CommandMessageHandlingMember} that forwards commands to a child entity.
 *
 * @param <P> the parent entity type
 * @param <C> the child entity type
 * @author Allard Buijze
 * @since 3.0
 */
public class ChildForwardingCommandMessageHandlingMember<P, C> implements ForwardingCommandMessageHandlingMember<P> {

    private final List<MessageHandlingMember<? super C>> childHandlingInterceptors;
    private final MessageHandlingMember<? super C> childHandler;
    private final BiFunction<CommandMessage<?>, P, C> childEntityResolver;
    private final String commandName;
    private final boolean isFactoryHandler;

    /**
     * Initializes a {@link ChildForwardingCommandMessageHandlingMember} that routes commands to a compatible child
     * entity. Child entities are resolved using the given {@code childEntityResolver}. If an entity is found the
     * command will be handled using the given {@code childHandler}.
     *
     * @param childHandlerInterceptors interceptors for {@code childHandler}
     * @param childHandler             handler of the command once a suitable entity is found
     * @param childEntityResolver      resolver of child entities for a given command
     */
    public ChildForwardingCommandMessageHandlingMember(List<MessageHandlingMember<? super C>> childHandlerInterceptors,
                                                       MessageHandlingMember<? super C> childHandler,
                                                       BiFunction<CommandMessage<?>, P, C> childEntityResolver) {
        this.childHandlingInterceptors = childHandlerInterceptors;
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
    public boolean canForward(CommandMessage<?> message, P target) {
        return childEntityResolver.apply(message, target) != null;
    }

    @Override
    public boolean canHandle(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        return childHandler.canHandle(message, context);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean canHandleMessageType(@Nonnull Class<? extends Message> messageType) {
        return childHandler.canHandleMessageType(messageType);
    }

    @Override
    public Object handleSync(@Nonnull Message<?> message, @Nonnull ProcessingContext context, @Nullable P target) throws Exception {
        C childEntity = childEntityResolver.apply((CommandMessage<?>) message, target);
        if (childEntity == null) {
            throw new ChildEntityNotFoundException(
                    "Aggregate cannot handle command [" + message.type()
                            + "], as there is no entity instance within the aggregate to forward it to."
            );
        }

        return interceptorChain(childEntity.getClass())
                .handleSync(message, childEntity, childHandler);
    }

    private MessageHandlerInterceptorMemberChain<C> interceptorChain(Class<?> childType) {
        return childHandlingInterceptors.isEmpty()
                ? NoMoreInterceptors.instance()
                : new ChainedMessageHandlerInterceptorMember<>(childType, childHandlingInterceptors.iterator());
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
    public <R> Optional<R> attribute(String attributeKey) {
        return childHandler.attribute(attributeKey);
    }
}
