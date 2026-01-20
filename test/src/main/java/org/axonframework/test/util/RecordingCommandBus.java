/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.util;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CommandBus implementation that does not perform any actions on subscriptions or dispatched commands, but records them
 * instead. This implementation is not a stand-in replacement for a mock, but might prove useful in many simple cases.
 *
 * @author Allard Buijze
 * @since 1.1
 */
@Internal
public class RecordingCommandBus implements CommandBus {

    private final ConcurrentMap<QualifiedName, CommandHandler> subscriptions = new ConcurrentHashMap<>();
    private final List<CommandMessage> dispatchedCommands = new ArrayList<>();
    private CallbackBehavior callbackBehavior = new DefaultCallbackBehavior();

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        dispatchedCommands.add(command);
        try {
            return CompletableFuture.completedFuture(asCommandResultMessage(
                    callbackBehavior.handle(command.payload(), command.metadata())
            ));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CommandBus subscribe(@Nonnull QualifiedName name,
                                @Nonnull CommandHandler handler) {
        CommandHandler commandHandler = Objects.requireNonNull(handler, "Given handler cannot be null.");
        subscriptions.putIfAbsent(name, commandHandler);
        return this;
    }

    private static CommandResultMessage asCommandResultMessage(@Nullable Object commandResult) {
        if (commandResult instanceof CommandResultMessage) {
            return (CommandResultMessage) commandResult;
        } else if (commandResult instanceof Message commandResultMessage) {
            return new GenericCommandResultMessage(commandResultMessage);
        }
        MessageType type = new MessageType(ObjectUtils.nullSafeTypeOf(commandResult));
        return new GenericCommandResultMessage(type, commandResult);
    }

    /**
     * Clears all the commands recorded by this Command Bus.
     */
    public void clearCommands() {
        dispatchedCommands.clear();
    }

    /**
     * Clears all subscribed handlers on this command bus.
     */
    public void clearSubscriptions() {
        subscriptions.clear();
    }

    /**
     * Indicates whether the given {@code commandHandler} is subscribed to this command bus.
     *
     * @param commandHandler The command handler to verify the subscription for
     * @return {@code true} if the handler is subscribed, otherwise {@code false}.
     */
    public boolean isSubscribed(CommandHandler commandHandler) {
        return subscriptions.containsValue(commandHandler);
    }

    /**
     * Indicates whether the given {@code commandHandler} is subscribed to commands of the given {@code commandType} on
     * this command bus.
     *
     * @param commandName    The name of the command to verify the subscription for
     * @param commandHandler The command handler to verify the subscription for
     * @return {@code true} if the handler is subscribed, otherwise {@code false}.
     */
    public boolean isSubscribed(QualifiedName commandName,
                                CommandHandler commandHandler) {
        return subscriptions.containsKey(commandName) && subscriptions.get(commandName).equals(commandHandler);
    }

    /**
     * Returns a Map will all Command Names and their Command Handler that have been subscribed to this command bus.
     *
     * @return a Map will all Command Names and their Command Handler
     */
    public Map<QualifiedName, CommandHandler> getSubscriptions() {
        return subscriptions;
    }

    /**
     * Returns a list with all commands that have been dispatched by this command bus.
     *
     * @return a list with all commands that have been dispatched
     */
    public List<CommandMessage> getDispatchedCommands() {
        return dispatchedCommands;
    }


    /**
     * Sets the instance that defines the behavior of the Command Bus when a command is dispatched with a callback.
     *
     * @param callbackBehavior The instance deciding to how the callback should be invoked.
     */
    public void setCallbackBehavior(CallbackBehavior callbackBehavior) {
        this.callbackBehavior = callbackBehavior;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("subscriptions", subscriptions);
        descriptor.describeProperty("dispatchedCommands", dispatchedCommands);
        descriptor.describeProperty("callbackBehavior", callbackBehavior);
    }
}
