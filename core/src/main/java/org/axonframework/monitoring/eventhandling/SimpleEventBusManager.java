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

package org.axonframework.monitoring.eventhandling;

import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.SimpleEventBusStatistics;
import org.axonframework.monitoring.ManagementContext;

import java.util.List;

/**
 * <p>JMX implementation to manage the monitor of the SimpleEventBus.</p>
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public class SimpleEventBusManager implements SimpleEventBusManagerMXBean {
    private SimpleEventBusStatistics statistics;

    public SimpleEventBusManager(SimpleEventBus simpleEventBus, ManagementContext context) {
        this.statistics = simpleEventBus.getStatistics();

        context.registerMBean(this, "SimpleEventBus");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAmountOfListeners() {
        return statistics.getAmountOfListeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getListeners() {
        return statistics.listeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAmountOfReceivedEvents() {
        return statistics.getAmountOfReceivedEvents();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetreceivedEvents() {
        statistics.resetEventsReceived();
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
