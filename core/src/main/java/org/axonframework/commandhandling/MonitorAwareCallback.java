package org.axonframework.commandhandling;

import org.axonframework.monitoring.MessageMonitor;

/**
 * Wrapper for a callback that notifies a Message Monitor of the message execution result.
 *
 * @param <R> the type of result of the command handling
 * @param <C> the type of payload of the command
 */
public class MonitorAwareCallback<C, R> implements CommandCallback<C, R> {

    private final CommandCallback<C, R> delegate;
    private final MessageMonitor.MonitorCallback messageMonitorCallback;

    /**
     * Initialize a callback wrapped around the {@code delegate} which will notify a Message Monitor for the given {@code messageMonitorCallback}.
     *
     * @param delegate the CommandCallback which is being wrapped, may be {@code null}
     * @param messageMonitorCallback the callback for the Message Monitor
     */
    public MonitorAwareCallback(CommandCallback<C, R> delegate, MessageMonitor.MonitorCallback messageMonitorCallback) {
        this.delegate = delegate;
        this.messageMonitorCallback = messageMonitorCallback;
    }

    @Override
    public void onSuccess(CommandMessage<? extends C> commandMessage, R result) {
        messageMonitorCallback.reportSuccess();
        if (delegate != null) {
            delegate.onSuccess(commandMessage, result);
        }
    }

    @Override
    public void onFailure(CommandMessage<? extends C> commandMessage, Throwable cause) {
        messageMonitorCallback.reportFailure(cause);
        if (delegate != null) {
            delegate.onFailure(commandMessage, cause);
        }
    }

}
