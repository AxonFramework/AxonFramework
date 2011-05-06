/*
 * Copyright (c) 2010-2011. Axon Framework
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CommandBus implementation that does not perform any actions on subscriptions or dispatched commands, but records
 * them instead. This implementation is not a stand-in replacement for a mock, but might prove useful in many simple
 * cases.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class RecordingCommandBus implements CommandBus {

    private Map<Class<?>, CommandHandler<?>> subscriptions = new HashMap<Class<?>, CommandHandler<?>>();
    private List<Object> dispatchedCommands = new ArrayList<Object>();

    @Override
    public void dispatch(Object command) {
        dispatchedCommands.add(command);
    }

    @Override
    public <R> void dispatch(Object command, CommandCallback<R> callback) {
        dispatchedCommands.add(command);
    }

    @Override
    public <C> void subscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        if (!subscriptions.containsKey(commandType)) {
            subscriptions.put(commandType, handler);
        }
    }

    @Override
    public <C> void unsubscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        subscriptions.remove(commandType);
    }

    public void clearCommands() {
        dispatchedCommands.clear();
    }

    public void clearSubscriptions() {
        subscriptions.clear();
    }

    public boolean isSubscribed(CommandHandler<?> commandHandler) {
        return subscriptions.containsValue(commandHandler);
    }

    public <C> boolean isSubscribed(Class<C> commandType, CommandHandler<? super C> commandHandler) {
        return subscriptions.containsKey(commandType) && subscriptions.get(commandType).equals(commandHandler);
    }

    public Map<Class<?>, CommandHandler<?>> getSubscriptions() {
        return subscriptions;
    }

    public List<Object> getDispatchedCommands() {
        return dispatchedCommands;
    }
}
