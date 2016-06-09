package org.axonframework.metrics;

/**
 * A NoOp MessageMonitor callback
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
class NoOpMessageMonitorCallback implements MessageMonitor.MonitorCallback {

    @Override
    public void reportSuccess() {
    }

    @Override
    public void reportFailure(Throwable cause) {
    }

    @Override
    public void reportIgnored() {

    }
}
