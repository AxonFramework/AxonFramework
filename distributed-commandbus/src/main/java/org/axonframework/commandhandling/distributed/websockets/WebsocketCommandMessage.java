package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandMessage;

/**
 * @author koen
 *         on 10-5-16.
 */
public class WebsocketCommandMessage<T> {
    private final CommandMessage<T> commandMessage;
    private final boolean withCallback;

    public WebsocketCommandMessage(CommandMessage<T> commandMessage, boolean withCallback) {
        this.commandMessage = commandMessage;
        this.withCallback = withCallback;
    }

    public boolean isWithCallback() {
        return withCallback;
    }

    public CommandMessage<T> getCommandMessage() {
        return commandMessage;
    }
}
