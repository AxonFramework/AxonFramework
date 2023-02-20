package org.axonframework.metrics;

import com.codahale.metrics.ConsoleReporter;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class GlobalMetricRegistryTest {

    private GlobalMetricRegistry subject;

    @BeforeEach
    void setUp() {
        subject = new GlobalMetricRegistry();
    }

    @Test
    void createEventProcessorMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor1 = subject.registerEventProcessor("test1");
        MessageMonitor<? super EventMessage<?>> monitor2 = subject.registerEventProcessor("test2");

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(subject.getRegistry()).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    void createEventBusMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor = subject.registerEventBus("eventBus");

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(subject.getRegistry()).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("eventBus"));
    }

    @Test
    void createCommandBusMonitor() {
        MessageMonitor<? super CommandMessage<?>> monitor = subject.registerCommandBus("commandBus");

        monitor.onMessageIngested(new GenericCommandMessage<>("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(subject.getRegistry()).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("commandBus"));
    }

    @Test
    void createMonitorForUnknownComponent() {
        MessageMonitor<? extends Message<?>> actual = subject.registerComponent(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }
}
