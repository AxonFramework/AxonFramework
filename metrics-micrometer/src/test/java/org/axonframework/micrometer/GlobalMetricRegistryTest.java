package org.axonframework.micrometer;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.junit.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

public class GlobalMetricRegistryTest {

    private GlobalMetricRegistry subject;

    private MetricRegistry dropWizardRegistry;

    @Before
    public void setUp() {
        DropwizardConfig config = new DropwizardConfig() {
            @Override
            public String prefix() {
                return "dropwizard";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
        dropWizardRegistry = new MetricRegistry();
        subject = new GlobalMetricRegistry(new DropwizardMeterRegistry(config,
                                                                       dropWizardRegistry,
                                                                       HierarchicalNameMapper.DEFAULT,
                                                                       Clock.SYSTEM) {
            @Override
            protected Double nullGaugeValue() {
                return null;
            }
        });
    }

    @Test
    public void createEventProcessorMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor1 = subject.registerEventProcessor("test1");
        MessageMonitor<? super EventMessage<?>> monitor2 = subject.registerEventProcessor("test2");

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    public void createEventBusMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor = subject.registerEventBus("eventBus");

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("eventBus"));
    }

    @Test
    public void createCommandBusMonitor() {
        MessageMonitor<? super CommandMessage<?>> monitor = subject.registerCommandBus("commandBus");

        monitor.onMessageIngested(new GenericCommandMessage<>("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("commandBus"));
    }

    @Test
    public void createMonitorForUnknownComponent() {
        MessageMonitor<? extends Message<?>> actual = subject.registerComponent(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }
}
