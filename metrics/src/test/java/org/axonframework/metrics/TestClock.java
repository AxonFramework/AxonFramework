package org.axonframework.metrics;

import com.codahale.metrics.Clock;

class TestClock extends Clock {

    private long currentTimeInMs = 0;

    @Override
    public long getTick() {
        return currentTimeInMs * 1000;
    }

    @Override
    public long getTime() {
        return currentTimeInMs;
    }

    public void increase(long increaseInMs) {
        this.currentTimeInMs += increaseInMs;
    }
}
