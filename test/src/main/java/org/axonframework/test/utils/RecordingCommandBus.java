/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.test.utils;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CommandBus implementation that does not perform any actions on subscriptions or dispatched commands, but records
 * them instead. This implementation is not a stand-in replacement for a mock, but might prove useful in many simple
 * cases.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class RecordingCommandBus implements CommandBus {

    private ConcurrentMap<Class<?>, CommandHandler<?>> subscriptions =
            new ConcurrentHashMap<Class<?>, CommandHandler<?>>();
    private List<CommandMessage<?>> dispatchedCommands = new ArrayList<CommandMessage<?>>();
    private CallbackBehavior callbackBehavior = new DefaultCallbackBehavior();

    @Override
    public void dispatch(CommandMessage<?> command) {
        dispatchedCommands.add(command);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> void dispatch(CommandMessage<?> command, CommandCallback<R> callback) {
        dispatchedCommands.add(command);
        try {
            callback.onSuccess((R) callbackBehavior.handle(command.getPayload(), command.getMetaData()));
        } catch (Throwable throwable) {
            callback.onFailure(throwable);
        }
    }

    @Override
    public <C> void subscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        if (!subscriptions.containsKey(commandType)) {
            subscriptions.put(commandType, handler);
        }
    }

    @Override
    public <C> boolean unsubscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        return subscriptions.remove(commandType, handler);
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
     * Indicates whether the given <code>commandHandler</code> is subscribed to this command bus.
     *
     * @param commandHandler The command handler to verify the subscription for
     * @return <code>true</code> if the handler is subscribed, otherwise <code>false</code>.
     */
    public boolean isSubscribed(CommandHandler<?> commandHandler) {
        return subscriptions.containsValue(commandHandler);
    }

    /**
     * Indicates whether the given <code>commandHandler</code> is subscribed to commands of the given
     * <code>commandType</code> on this command bus.
     *
     * @param commandType    The type of command to verify the subscription for
     * @param commandHandler The command handler to verify the subscription for
     * @param <C>            The type of command to verify the subscription for
     * @return <code>true</code> if the handler is subscribed, otherwise <code>false</code>.
     */
    public <C> boolean isSubscribed(Class<C> commandType, CommandHandler<? super C> commandHandler) {
        return subscriptions.containsKey(commandType) && subscriptions.get(commandType).equals(commandHandler);
    }

    /**
     * Returns a Map will all Command Types and their Command Handler that have been subscribed to this command bus.
     *
     * @return a Map will all Command Types and their Command Handler
     */
    public Map<Class<?>, CommandHandler<?>> getSubscriptions() {
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
}
