package org.axonframework.commandhandling.distributed;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author koen
 *         on 11-5-16.
 */
public class CommandCallbackRepository<A> {
    final Map<String, CommandCallbackWrapper> callbacks = new ConcurrentHashMap<>();

    public void cancelCallbacks(A channelId) {
        Iterator<CommandCallbackWrapper> callbacks = this.callbacks.values().iterator();
        while (callbacks.hasNext()) {
            CommandCallbackWrapper wrapper = callbacks.next();
            if (wrapper.getChannelIdentifier().equals(channelId)) {
                wrapper.fail(new CommandBusConnectorCommunicationException(String.format(
                        "Connection error while waiting for a response on command %s",
                        wrapper.getMessage().getCommandName())));
                callbacks.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <A, C, R> CommandCallbackWrapper<A, C, R> fetchAndRemove(String callbackId) {
        return callbacks.remove(callbackId);
    }

    public <A, C, R> void store(String callbackId, CommandCallbackWrapper<A, C, R> commandCallbackWrapper) {
        CommandCallbackWrapper previous;
        if ((previous = callbacks.put(callbackId, commandCallbackWrapper)) != null) {
            //a previous callback with the same command ID was already found, we will cancel the callback as the command
            //is likely to be retried, so the previous one likely failed
            previous.fail(new CommandBusConnectorCommunicationException(
                    "Command-callback cancelled, a new command with the same ID is entered into the command bus"));
        }
    }
}
