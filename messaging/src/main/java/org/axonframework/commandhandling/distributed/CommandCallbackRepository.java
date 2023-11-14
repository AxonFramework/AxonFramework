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

package org.axonframework.commandhandling.distributed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

/**
 * This class retains a list of callbacks for CommandCallbackConnectors to use.
 *
 * @param <A> The type of the endpoint identifier.
 */
public class CommandCallbackRepository<A> {

    private final Map<String, CommandCallbackWrapper> callbacks = new ConcurrentHashMap<>();

    /**
     * Removes all callbacks for a given channel. Registered callbacks will receive a failure response containing a
     * {@link CommandBusConnectorCommunicationException}.
     *
     * @param channelId the channel identifier
     * @return the collection of removed callbacks
     */
    public Collection<CommandCallbackWrapper> cancelCallbacksForChannel(A channelId) {
        Iterator<CommandCallbackWrapper> callbacks = this.callbacks.values().iterator();
        ArrayList<CommandCallbackWrapper> removed = new ArrayList<>();
        while (callbacks.hasNext()) {
            CommandCallbackWrapper wrapper = callbacks.next();
            if (wrapper.getChannelIdentifier().equals(channelId)) {
                wrapper.reportResult(asCommandResultMessage(new CommandBusConnectorCommunicationException(
                        String.format("Connection error while waiting for a response on command %s",
                                      wrapper.getMessage().getCommandName()))));
                callbacks.remove();
                removed.add(wrapper);
            }
        }
        return removed;
    }

    /**
     * Fetches and removes a callback. The callback will not be used a second time, so removal should be fine
     *
     * @param callbackId The Callback Id to fetch the callback for
     * @param <E>        The type of the remote endpoint identifier
     * @param <C>        The type of the command
     * @param <R>        The type of the result
     * @return The stored CommandCallbackWrapper or null if not found
     */
    @SuppressWarnings("unchecked")
    public <E, C, R> CommandCallbackWrapper<E, C, R> fetchAndRemove(String callbackId) {
        return callbacks.remove(callbackId);
    }

    /**
     * Stores a callback
     *
     * @param callbackId             The id to store the callback with
     * @param commandCallbackWrapper The CommandCallbackWrapper to store
     * @param <E>                    The type of the remote endpoint identifier
     * @param <C>                    The type of the command
     * @param <R>                    The type of the result
     */
    public <E, C, R> void store(String callbackId, CommandCallbackWrapper<E, C, R> commandCallbackWrapper) {
        CommandCallbackWrapper previous;
        if ((previous = callbacks.put(callbackId, commandCallbackWrapper)) != null) {
            //a previous callback with the same command ID was already found, we will cancel the callback as the command
            //is likely to be retried, so the previous one likely failed
            previous.reportResult(asCommandResultMessage(new CommandBusConnectorCommunicationException(
                    "Command-callback cancelled, a new command with the same ID is entered into the command bus")));
        }
    }

    /**
     * Returns the callbacks mapped by callback identifier.
     *
     * @return the command callbacks by id
     */
    protected Map<String, CommandCallbackWrapper> callbacks() {
        return callbacks;
    }
}
