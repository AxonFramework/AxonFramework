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

package org.axonframework.test.utils;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

/**
 * CommandBus implementation that does not perform any actions on subscriptions or dispatched commands, but records
 * them instead. This implementation is not a stand-in replacement for a mock, but might prove useful in many simple
 * cases.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class RecordingCommandBus implements CommandBus {

    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>>> subscriptions = new ConcurrentHashMap<>();
    private final List<CommandMessage<?>> dispatchedCommands = new ArrayList<>();
    private CallbackBehavior callbackBehavior = new DefaultCallbackBehavior();

    @Override
    public <C> void dispatch(@Nonnull CommandMessage<C> command) {
        dispatchedCommands.add(command);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C, R> void dispatch(@Nonnull CommandMessage<C> command,
                                @Nonnull CommandCallback<? super C, ? super R> callback) {
        dispatchedCommands.add(command);
        try {
            callback.onResult(command, asCommandResultMessage(
                    callbackBehavior.handle(command.getPayload(), command.getMetaData())
            ));
        } catch (Throwable throwable) {
            callback.onResult(command, asCommandResultMessage(throwable));
        }
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>> handler) {
        subscriptions.putIfAbsent(commandName, handler);
        return () -> subscriptions.remove(commandName, handler);
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
    public boolean isSubscribed(MessageHandler<? super CommandMessage<?>> commandHandler) {
        return subscriptions.containsValue(commandHandler);
    }

    /**
     * Indicates whether the given {@code commandHandler} is subscribed to commands of the given
     * {@code commandType} on this command bus.
     *
     * @param commandName    The name of the command to verify the subscription for
     * @param commandHandler The command handler to verify the subscription for
     * @param <C>            The type of command to verify the subscription for
     * @return {@code true} if the handler is subscribed, otherwise {@code false}.
     */
    public <C> boolean isSubscribed(String commandName, MessageHandler<? super CommandMessage<?>> commandHandler) {
        return subscriptions.containsKey(commandName) && subscriptions.get(commandName).equals(commandHandler);
    }

    /**
     * Returns a Map will all Command Names and their Command Handler that have been subscribed to this command bus.
     *
     * @return a Map will all Command Names and their Command Handler
     */
    public Map<String, MessageHandler<? super CommandMessage<?>>> getSubscriptions() {
        return subscriptions;
    }

    /**
     * Returns a list with all commands that have been dispatched by this command bus.
     *
     * @return a list with all commands that have been dispatched
     */
    public List<CommandMessage<?>> getDispatchedCommands() {
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
    public Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return null;
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return () -> true;
    }
}
