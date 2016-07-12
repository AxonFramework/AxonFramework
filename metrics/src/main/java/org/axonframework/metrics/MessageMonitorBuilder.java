/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.metrics;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MultiMessageMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessageMonitorBuilder {

    public MessageMonitor<EventMessage<?>> buildEventProcessorMonitor(MetricRegistry globalRegistry) {
        MessageTimerMonitor messageTimerMonitor = new MessageTimerMonitor();
        EventProcessorLatencyMonitor eventProcessorLatencyMonitor = new EventProcessorLatencyMonitor();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);
        MessageCountingMonitor messageCountingMonitor = new MessageCountingMonitor();

        MetricRegistry eventProcessingRegistry = new MetricRegistry();
        eventProcessingRegistry.register("messageTimer", messageTimerMonitor);
        eventProcessingRegistry.register("latency", eventProcessorLatencyMonitor);
        eventProcessingRegistry.register("messageCounter", messageCountingMonitor);
        globalRegistry.register("eventProcessing", eventProcessingRegistry);

        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(messageTimerMonitor);
        monitors.add(eventProcessorLatencyMonitor);
        monitors.add(capacityMonitor);
        monitors.add(messageCountingMonitor);
        return new MultiMessageMonitor<>(monitors);
    }

    public MessageMonitor<EventMessage<?>> buildEventBusMonitor(MetricRegistry globalRegistry) {
        MessageTimerMonitor messageTimerMonitor = new MessageTimerMonitor();

        MetricRegistry eventProcessingRegistry = new MetricRegistry();
        eventProcessingRegistry.register("messageTimer", messageTimerMonitor);
        globalRegistry.register("eventBus", eventProcessingRegistry);

        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(messageTimerMonitor);
        return new MultiMessageMonitor<>(monitors);
    }

    public MessageMonitor<CommandMessage<?>> buildCommandBusMonitor(MetricRegistry globalRegistry) {
        MessageTimerMonitor messageTimerMonitor = new MessageTimerMonitor();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);
        MessageCountingMonitor messageCountingMonitor = new MessageCountingMonitor();

        MetricRegistry commandHandlingRegistry = new MetricRegistry();
        commandHandlingRegistry.register("messageTimer", messageTimerMonitor);
        commandHandlingRegistry.register("capacity", capacityMonitor);
        commandHandlingRegistry.register("messageCounter", messageCountingMonitor);
        globalRegistry.register("commandHandling", commandHandlingRegistry);

        return new MultiMessageMonitor<>(messageTimerMonitor, capacityMonitor, messageCountingMonitor);
    }
}

