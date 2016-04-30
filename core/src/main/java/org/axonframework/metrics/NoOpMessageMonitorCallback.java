package org.axonframework.metrics;

import java.util.Optional;

/**
 * A NoOp MessageMonitor callback
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class NoOpMessageMonitorCallback implements MessageMonitor.MonitorCallback {

    @Override
    public void onSuccess() {
    }

    @Override
    public void onFailure(Optional<Throwable> cause) {
    }
}
