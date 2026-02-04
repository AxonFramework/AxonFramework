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

package org.axonframework.messaging.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandPriorityCalculator;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.RoutingStrategy;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link CommandGateway} interface.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class DefaultCommandGateway implements CommandGateway {

    private final CommandBus commandBus;
    private final MessageTypeResolver messageTypeResolver;
    private final CommandPriorityCalculator priorityCalculator;
    private final RoutingStrategy routingKeyResolver;

    /**
     * Initialize the {@code DefaultCommandGateway} to send commands through given {@code commandBus}.
     * <p>
     * The {@link QualifiedName names} for
     * {@link CommandMessage CommandMessages} are resolved through the given
     * {@code nameResolver}.
     *
     * @param commandBus          The {@link CommandBus} to send commands on.
     * @param messageTypeResolver The {@link MessageTypeResolver} resolving the
     *                            {@link QualifiedName names} for
     *                            {@link CommandMessage CommandMessages} being
     *                            dispatched on the {@code commandBus}.
     * @param priorityCalculator  The {@link CommandPriorityCalculator} determining the priority of commands.
     * @param routingKeyResolver  The {@link RoutingStrategy} determining the routing key for commands.
     */
    public DefaultCommandGateway(@Nonnull CommandBus commandBus,
                                 @Nonnull MessageTypeResolver messageTypeResolver,
                                 @Nonnull CommandPriorityCalculator priorityCalculator,
                                 @Nonnull RoutingStrategy routingKeyResolver) {
        this.commandBus = requireNonNull(commandBus, "The commandBus may not be null.");
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The messageTypeResolver may not be null.");
        this.priorityCalculator = requireNonNull(priorityCalculator, "The CommandPriorityCalculator may not be null.");
        this.routingKeyResolver = requireNonNull(routingKeyResolver, "The RoutingStrategy may not be null.");
    }

    @Override
    @Nonnull
    public CommandResult send(@Nonnull Object command,
                              @Nonnull Metadata metadata,
                              @Nullable ProcessingContext context) {
        return new FutureCommandResult(commandBus.dispatch(asCommandMessage(command, metadata), context));
    }

    /**
     * Returns the given command as a {@link CommandMessage}.
     * <p>
     * If {@code command} already implements {@code CommandMessage}, it is returned as-is. When the {@code command} is
     * another implementation of {@link Message}, the {@link Message#payload()} and {@link Message#metadata()} are used
     * as input for a new {@link GenericCommandMessage}. Otherwise, the given {@code command} is wrapped into a
     * {@code GenericCommandMessage} as its payload.
     * <p>
     * When {@link CommandMessage#routingKey()} or {@link CommandMessage#priority()} are {@link Optional#empty() empty},
     * the configured {@link CommandPriorityCalculator} and {@link RoutingStrategy} will be invoked.
     *
     * @param command The command to wrap as {@link CommandMessage}.
     * @return A {@link CommandMessage} containing given {@code command} as payload, a {@code command} if it already
     * implements {@code CommandMessage}, or a {@code CommandMessage} based on the result of {@link Message#payload()}
     * and {@link Message#metadata()} for other {@link Message} implementations.
     */
    private CommandMessage asCommandMessage(Object command, Metadata metadata) {
        CommandMessage commandMessage;
        if (command instanceof CommandMessage) {
            commandMessage = (CommandMessage) command;
        } else {
            commandMessage = command instanceof Message message
                    ? new GenericCommandMessage(message.type(), message.payload(), message.metadata())
                    : new GenericCommandMessage(messageTypeResolver.resolveOrThrow(command), command, metadata);
        }
        return new GenericCommandMessage(
                commandMessage,
                commandMessage.routingKey().orElse(routingKeyResolver.getRoutingKey(commandMessage)),
                commandMessage.priority().orElse(priorityCalculator.determinePriority(commandMessage))
        );
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandBus", commandBus);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
        descriptor.describeProperty("priorityCalculator", priorityCalculator);
        descriptor.describeProperty("routingKeyResolver", routingKeyResolver);
    }
}
