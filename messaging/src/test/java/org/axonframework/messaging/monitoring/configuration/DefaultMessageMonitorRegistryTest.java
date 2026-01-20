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

package org.axonframework.messaging.monitoring.configuration;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MessageMonitor.MonitorCallback;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitorCallback;
import org.axonframework.messaging.monitoring.configuration.DefaultMessageMonitorRegistry;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultMessageMonitorRegistryTest {

    private final DefaultMessageMonitorRegistry testSubject = new DefaultMessageMonitorRegistry();
    private final Configuration config = mock(Configuration.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void registersCommandMessageMonitorAndProvidesMultiMessageMonitor() {
        final var monitor = commandMessageMonitor();

        testSubject.registerCommandMonitor(c -> monitor);
        final var result = testSubject.commandMonitor(config);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).containsExactly(monitor);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void registersEventMessageMonitorAndProvidesMultiMessageMonitor() {
        final var monitor = eventMessageMonitor();

        testSubject.registerEventMonitor(c -> monitor);
        final var result = testSubject.eventMonitor(config);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).containsExactly(monitor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void registersQueryMessageMonitorAndProvidesMultiMessageMonitor() {
        final var monitor = queryMessageMonitor();

        testSubject.registerQueryMonitor(c -> monitor);
        final var result = testSubject.queryMonitor(config);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).containsExactly(monitor);
    }

    @Test
    void providesNoopMessageMonitorWhenNoMonitorsAreRegistered() {
        assertThat(testSubject.commandMonitor(config)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.eventMonitor(config)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.queryMonitor(config)).isInstanceOf(NoOpMessageMonitor.class);
    }

    @SuppressWarnings("rawtypes")
    @Test
    void registersGenericMessageMonitorAsThreeSpecializedMonitors() {

        testSubject.registerMonitor(c -> genericMessageMonitor());

        // Each resolver should give a MultiMessageMonitor with exactly one underlying specialized monitor

        MessageMonitor<? super CommandMessage> commandMonitor = testSubject.commandMonitor(config);
        assertThat(commandMonitor).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) commandMonitor).messageMonitors()).hasSize(1);

        MessageMonitor<? super EventMessage> eventMonitor = testSubject.eventMonitor(config);
        assertThat(eventMonitor).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) eventMonitor).messageMonitors()).hasSize(1);

        MessageMonitor<? super QueryMessage> queryMonitor = testSubject.queryMonitor(config);
        assertThat(queryMonitor).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) queryMonitor).messageMonitors()).hasSize(1);
    }

    @Test
    void genericMonitorDelegatesToSpecializedTypes() {
        final AtomicReference<CommandMessage> capturedCommand = new AtomicReference<>();
        final AtomicReference<EventMessage> capturedEvent = new AtomicReference<>();
        final AtomicReference<QueryMessage> capturedQuery = new AtomicReference<>();

        MessageMonitor<Message> capturing = message -> new MonitorCallback() {
            @Override
            public void reportSuccess() {
                switch (message) {
                    case CommandMessage commandMessage -> capturedCommand.set(commandMessage);
                    case EventMessage eventMessage -> capturedEvent.set(eventMessage);
                    case QueryMessage queryMessage -> capturedQuery.set(queryMessage);
                    default -> throw new IllegalArgumentException("Unexpected message type: " + message.getClass());
                }
            }

            @Override
            public void reportFailure(Throwable cause) {
                // noop
            }

            @Override
            public void reportIgnored() {
                // noop
            }
        };
        testSubject.registerMonitor(c -> capturing);

        CommandMessage command = mock(CommandMessage.class);
        EventMessage event = mock(EventMessage.class);
        QueryMessage query = mock(QueryMessage.class);

        testSubject.commandMonitor(config).onMessageIngested(command).reportSuccess();
        testSubject.eventMonitor(config).onMessageIngested(event).reportSuccess();
        testSubject.queryMonitor(config).onMessageIngested(query).reportSuccess();

        assertThat(capturedCommand).hasValue(command);
        assertThat(capturedEvent).hasValue(event);
        assertThat(capturedQuery).hasValue(query);
    }

    private static MessageMonitor<? super EventMessage> eventMessageMonitor() {
        return (MessageMonitor<EventMessage>) message -> NoOpMessageMonitorCallback.INSTANCE;
    }

    private static MessageMonitor<Message> genericMessageMonitor() {
        return message -> NoOpMessageMonitorCallback.INSTANCE;
    }

    private static MessageMonitor<? super CommandMessage> commandMessageMonitor() {
        return (MessageMonitor<CommandMessage>) message -> NoOpMessageMonitorCallback.INSTANCE;
    }

    private static MessageMonitor<? super QueryMessage> queryMessageMonitor() {
        return (MessageMonitor<QueryMessage>) message -> NoOpMessageMonitorCallback.INSTANCE;
    }
}
