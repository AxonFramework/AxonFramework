package org.axonframework.metrics;

import org.axonframework.messaging.Message;

/**
 * A message monitor that returns a NoOp message callback
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public enum NoOpMessageMonitor implements MessageMonitor<Message<?>> {

    INSTANCE;

    private final static NoOpMessageMonitorCallback NO_OP_MESSAGE_MONITOR_CALLBACK = new NoOpMessageMonitorCallback();

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
        return NO_OP_MESSAGE_MONITOR_CALLBACK;
    }

}
