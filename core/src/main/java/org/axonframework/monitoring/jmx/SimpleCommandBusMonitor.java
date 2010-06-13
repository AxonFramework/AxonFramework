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

import org.axonframework.commandhandling.SimpleCommandBusStatistics;
import org.axonframework.monitoring.Monitored;
import org.axonframework.monitoring.Statistics;

import java.util.List;

/**
 * <p>JMX implementation to manage the monitor of the SimpleCommandBus.</p>
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public class SimpleCommandBusMonitor implements SimpleCommandBusMonitorMXBean, Statistics {

    private SimpleCommandBusStatistics statistics;

    /**
     * Creates an instance of this monitor for the given <code>commandBus</code>.
     *
     * @param simpleCommandBus The command bus to monitor
     */
    public SimpleCommandBusMonitor(Monitored<SimpleCommandBusStatistics> simpleCommandBus) {
        this.statistics = simpleCommandBus.getStatistics();
    }

    @Override
    public long getCommandHandlerCount() {
        return statistics.getCommandHandlerCount();
    }

    @Override
    public List<String> getHandlerTypes() {
        return statistics.getHandlerTypes();
    }

    @Override
    public long getReceivedCommandCount() {
        return statistics.getReceivedCommandCount();
    }

    @Override
    public void resetReceivedCommandsCounter() {
        statistics.resetReceivedCommandsCounter();
    }

    @Override
    public boolean isEnabled() {
        return statistics.isEnabled();
    }

    @Override
    public void enable() {
        statistics.enable();
    }

    @Override
    public void disable() {
        statistics.disable();
    }
}
