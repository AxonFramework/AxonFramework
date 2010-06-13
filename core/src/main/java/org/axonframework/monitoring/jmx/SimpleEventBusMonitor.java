/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.monitoring.jmx;

import org.axonframework.eventhandling.SimpleEventBusStatistics;
import org.axonframework.monitoring.Monitored;
import org.axonframework.monitoring.Statistics;

import java.util.List;

/**
 * JMX implementation to manage the monitor of the SimpleEventBus.
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public class SimpleEventBusMonitor implements SimpleEventBusMonitorMXBean, Statistics {

    private SimpleEventBusStatistics statistics;

    /**
     * Creates an instance of the monitor for the given <code>simpleEventBus</code>.
     *
     * @param simpleEventBus Used to obtain the statistics object from
     */
    public SimpleEventBusMonitor(Monitored<SimpleEventBusStatistics> simpleEventBus) {
        this.statistics = simpleEventBus.getStatistics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getListenerCount() {
        return statistics.getListenerCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getListenerTypes() {
        return statistics.listeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getReceivedEventsCount() {
        return statistics.getPublishedEventCounter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetReceivedEventsCount() {
        statistics.resetReceivedEventCount();
    }

    @Override
    public boolean isEnabled() {
        return statistics.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable() {
        statistics.enable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disable() {
        statistics.disable();
    }
}
