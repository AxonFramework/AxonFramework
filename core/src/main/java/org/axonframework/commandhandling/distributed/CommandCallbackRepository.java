package org.axonframework.commandhandling.distributed;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     */
    public void cancelCallbacks(A channelId) {
        Iterator<CommandCallbackWrapper> callbacks = this.callbacks.values().iterator();
        while (callbacks.hasNext()) {
            CommandCallbackWrapper wrapper = callbacks.next();
            if (wrapper.getChannelIdentifier().equals(channelId)) {
                wrapper.fail(new CommandBusConnectorCommunicationException(
                        String.format("Connection error while waiting for a response on command %s",
                                      wrapper.getMessage().getCommandName())));
                callbacks.remove();
            }
        }
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
            previous.fail(new CommandBusConnectorCommunicationException(
                    "Command-callback cancelled, a new command with the same ID is entered into the command bus"));
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
