package org.axonframework.commandhandling.distributed.websockets;

/**
 * @author koen
 *         on 10-5-16.
 */
public class WebsocketResultMessage<R> {
    private final String commandId;
    private final R result;
    private final Throwable cause;

    public WebsocketResultMessage(String commandId, R result, Throwable cause) {
        this.commandId = commandId;
        this.result = result;
        this.cause = cause;
    }

    public R getResult() {
        return result;
    }

    public Throwable getCause() {
        return cause;
    }

    public String getCommandId() {
        return commandId;
    }
}
