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

package org.axonframework.monitoring.jmx.commandhandling;

import org.axonframework.commandhandling.SimpleCommandBusStatistics;
import org.axonframework.monitoring.Monitored;
import org.axonframework.monitoring.jmx.ManagementContext;

import java.util.List;

/**
 * <p>JMX implementation to manage the monitor of the SimpleCommandBus.</p>
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public class SimpleCommandBusManager implements SimpleCommandBusManagerMXBean {
    private SimpleCommandBusStatistics statistics;

    public SimpleCommandBusManager(Monitored<SimpleCommandBusStatistics> commandBus, ManagementContext context) {
        this.statistics = commandBus.getStatistics();
        context.registerMBean(this, "SimpleCommandBus");
    }

    @Override
    public long getAmountOfHandlers() {
        return statistics.getAmountOfHandlers();
    }

    @Override
    public List<String> getHandlers() {
        return statistics.getHandlers();
    }

    @Override
    public long getAmountOfReceivedCommands() {
        return statistics.getAmountOfReceivedCommands();
    }

    @Override
    public void resetReceivedCommandsCounter() {
        statistics.resetCommandsReceived();
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
