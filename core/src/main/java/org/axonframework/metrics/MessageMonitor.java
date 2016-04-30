package org.axonframework.metrics;

import org.axonframework.messaging.Message;

import java.util.Optional;

/**
 * Specifies a mechanism to monitor message processing. When a message is supplied to
 * a message monitor it returns a callback which should be used to notify the message monitor
 * of the result of the processing of the event.
 *
 * For example, a message monitor can track various things like message processing times, failure and success rates and
 * occurred exceptions. It also can gather information contained in messages headers like timestamps and tracers
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public interface MessageMonitor<T extends Message<?>> {

    /**
     * Takes a message and returns a callback that should be used
     * to inform the message monitor about the result of processing the message
     * @param message the message to monitor
     * @return the callback
     */
    MonitorCallback onMessageIngested(T message);

    /**
     * An interface to let the message processor inform the message monitor of the result
     * of processing the message
     */
    interface MonitorCallback {

        /**
         * Notify the monitor that the message was handled successfully
         */
        void onSuccess();

        /**
         * Notify the monitor that a failure occurred during processing of the message
         */
        void onFailure(Optional<Throwable> cause);
    }
}
