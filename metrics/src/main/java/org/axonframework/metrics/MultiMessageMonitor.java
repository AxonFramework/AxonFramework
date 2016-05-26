package org.axonframework.metrics;

import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Delegates messages and callbacks to the given list of message monitors
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class MultiMessageMonitor<T extends Message<?>> implements MessageMonitor<T> {

    private final List<MessageMonitor<? super T>> messageMonitors;

    /**
     * Initialize a message monitor with the given list of <name>messageMonitors</name>
     *
     * @param messageMonitors the list of event monitors to delegate to
     */
    public MultiMessageMonitor(List<MessageMonitor<? super T>> messageMonitors) {
        Assert.notNull(messageMonitors, "MessageMonitor list may not be null");
        this.messageMonitors = new ArrayList<>(messageMonitors);
    }

    /**
     * Calls the message monitors with the given message and returns a callback
     * that will trigger all the message monitor callbacks
     *
     * @param message the message to delegate to the message monitors
     * @return the callback that will trigger all the message monitor callbacks
     */
    @Override
    public MonitorCallback onMessageIngested(T message) {
        final List<MonitorCallback> monitorCallbacks = messageMonitors.stream()
                .map(messageMonitor -> messageMonitor.onMessageIngested(message))
                .collect(Collectors.toList());

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                monitorCallbacks.forEach(MonitorCallback::reportSuccess);
            }

            @Override
            public void reportFailure(Throwable cause) {
                monitorCallbacks.forEach(resultCallback -> resultCallback.reportFailure(cause));
            }

            @Override
            public void reportIgnored() {
                monitorCallbacks.forEach(MonitorCallback::reportIgnored);
            }
        };
    }
}