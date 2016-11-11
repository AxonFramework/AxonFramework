package org.axonframework.metrics;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertTrue;

public class MessageMonitorFactoryTest {

    @Test
    public void createEventProcessorMonitor() throws Exception {
        MetricRegistry globalRegistry = new MetricRegistry();
        MessageMonitor<EventMessage<?>> monitor1 = MessageMonitorFactory.createEventProcessorMonitor("test1", globalRegistry);
        MessageMonitor<EventMessage<?>> monitor2 = MessageMonitorFactory.createEventProcessorMonitor("test2", globalRegistry);

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(globalRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    public void createEventBusMonitor() throws Exception {
        MetricRegistry globalRegistry = new MetricRegistry();
        MessageMonitor<EventMessage<?>> monitor = MessageMonitorFactory.createEventBusMonitor(globalRegistry);

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(globalRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("eventBus"));
    }

    @Test
    public void createCommandBusMonitor() throws Exception {
        MetricRegistry globalRegistry = new MetricRegistry();
        MessageMonitor<CommandMessage<?>> monitor = MessageMonitorFactory.createCommandBusMonitor(globalRegistry);

        monitor.onMessageIngested(new GenericCommandMessage<>("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(globalRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("commandHandling"));
    }

}
