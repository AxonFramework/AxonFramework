/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.metrics;

import com.codahale.metrics.ConsoleReporter;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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
        String output = out.toString();

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    void createEventBusMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor = subject.registerEventBus("eventBus");

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(subject.getRegistry()).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("eventBus"));
    }

    @Test
    void createCommandBusMonitor() {
        MessageMonitor<? super CommandMessage<?>> monitor = subject.registerCommandBus("commandBus");

        monitor.onMessageIngested(new GenericCommandMessage<>(new QualifiedName("test", "command", "0.0.1"), "test"))
               .reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(subject.getRegistry()).outputTo(new PrintStream(out)).build().report();
        String output = out.toString();

        assertTrue(output.contains("commandBus"));
    }

    @Test
    void createMonitorForUnknownComponent() {
        MessageMonitor<? extends Message<?>> actual = subject.registerComponent(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }

    private static EventMessage<Object> asEventMessage(String payload) {
        return new GenericEventMessage<>(new QualifiedName("test", "event", "0.0.1"), payload);
    }
}
