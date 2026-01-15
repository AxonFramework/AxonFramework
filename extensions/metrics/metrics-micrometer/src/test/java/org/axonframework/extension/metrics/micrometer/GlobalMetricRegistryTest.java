/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.metrics.micrometer;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class GlobalMetricRegistryTest {

    private GlobalMetricRegistry subject;

    private MetricRegistry dropWizardRegistry;

    @BeforeEach
    void setUp() {
        DropwizardConfig config = new DropwizardConfig() {
            @Nonnull
            @Override
            public String prefix() {
                return "dropwizard";
            }

            @Override
            public String get(@Nonnull String key) {
                return null;
            }
        };
        dropWizardRegistry = new MetricRegistry();
        subject = new GlobalMetricRegistry(new DropwizardMeterRegistry(config,
                                                                       dropWizardRegistry,
                                                                       HierarchicalNameMapper.DEFAULT,
                                                                       Clock.SYSTEM) {
            @Nonnull
            @Override
            protected Double nullGaugeValue() {
                return 0.0;
            }
        });
    }

    @Test
    void createEventProcessorMonitor() {
        MessageMonitor<? super EventMessage> monitor1 = subject.registerEventProcessor("test1");
        MessageMonitor<? super EventMessage> monitor2 = subject.registerEventProcessor("test2");

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    void createEventProcessorMonitorWithTags() {
        MessageMonitor<? super EventMessage> monitor1 = subject.registerEventProcessor(
                "test1",
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.payloadType().getSimpleName()),
                message -> Tags.empty());
        MessageMonitor<? super EventMessage> monitor2 = subject.registerEventProcessor(
                "test2",
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.payloadType().getSimpleName()),
                message -> Tags.empty());

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    void createEventBusMonitor() {
        MessageMonitor<? super EventMessage> monitor = subject.registerEventBus("eventBus");

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("eventBus"));
    }

    @Test
    void createEventBusMonitorWithTags() {
        MessageMonitor<? super EventMessage> monitor = subject.registerEventBus(
                "eventBus", message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.payloadType().getSimpleName())
        );

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("eventBus"));
    }

    @Test
    void createCommandBusMonitor() {
        MessageMonitor<? super CommandMessage> monitor = subject.registerCommandBus("commandBus");

        monitor.onMessageIngested(new GenericCommandMessage(new MessageType("command"), "test"))
               .reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("commandBus"));
    }

    @Test
    void createCommandBusMonitorWithTags() {
        MessageMonitor<? super CommandMessage> monitor = subject.registerCommandBus(
                "commandBus", message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.payloadType().getSimpleName())
        );

        monitor.onMessageIngested(new GenericCommandMessage(new MessageType("command"), "test"))
               .reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("commandBus"));
    }

    @Test
    void createMonitorForUnknownComponent() {
        MessageMonitor<? extends Message> actual = subject.registerComponent(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }

    @Test
    void createMonitorForUnknownComponentWithTags() {
        MessageMonitor<? extends Message> actual = subject.registerComponentWithDefaultTags(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }

    private static EventMessage asEventMessage(Object payload) {
        return new GenericEventMessage(new MessageType("event"), payload);
    }
}
