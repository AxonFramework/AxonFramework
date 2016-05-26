package org.axonframework.metrics;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessageMonitorBuilder {

    public MessageMonitor<EventMessage<?>> buildEventProcessorMonitor(MetricRegistry globalRegistry){
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

    public MessageMonitor<EventMessage<?>> buildEventBusMonitor(MetricRegistry globalRegistry){
        MessageTimerMonitor messageTimerMonitor = new MessageTimerMonitor();

        MetricRegistry eventProcessingRegistry = new MetricRegistry();
        eventProcessingRegistry.register("messageTimer", messageTimerMonitor);
        globalRegistry.register("eventBus", eventProcessingRegistry);

        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(messageTimerMonitor);
        return new MultiMessageMonitor<>(monitors);
    }

    public MessageMonitor<CommandMessage<?>> buildCommandBusMonitor(MetricRegistry globalRegistry){
        MessageTimerMonitor messageTimerMonitor = new MessageTimerMonitor();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);
        MessageCountingMonitor messageCountingMonitor = new MessageCountingMonitor();

        MetricRegistry commandHandlingRegistry = new MetricRegistry();
        commandHandlingRegistry.register("messageTimer", messageTimerMonitor);
        commandHandlingRegistry.register("capacity", capacityMonitor);
        commandHandlingRegistry.register("messageCounter", messageCountingMonitor);
        globalRegistry.register("commandHandling", commandHandlingRegistry);

        List<MessageMonitor<? super CommandMessage<?>>> monitors = new ArrayList<>();
        monitors.add(messageTimerMonitor);
        monitors.add(capacityMonitor);
        monitors.add(messageCountingMonitor);
        return new MultiMessageMonitor<>(monitors);
    }
}

