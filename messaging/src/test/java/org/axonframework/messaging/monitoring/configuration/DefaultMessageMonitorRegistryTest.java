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

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MessageMonitor.MonitorCallback;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitorCallback;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultMessageMonitorRegistry}.
 *
 * @author Jan Galinski
 */
class DefaultMessageMonitorRegistryTest {

    private final DefaultMessageMonitorRegistry testSubject = new DefaultMessageMonitorRegistry();
    private final Configuration config = mock(Configuration.class);

    @Test
    void registeringSingleCommandMonitorResultsInRegisteredMonitor() {
        final var monitor = commandMessageMonitor();

        testSubject.registerCommandMonitor(c -> monitor);
        final var result = testSubject.commandMonitor(config, CommandBus.class, null);

        assertThat(result).isEqualTo(monitor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void registeringSeveralCommandMonitorsResultsInMultiMessageMonitor() {
        final var monitorOne = commandMessageMonitor();
        final var monitorTwo = commandMessageMonitor();

        testSubject.registerCommandMonitor(c -> monitorOne)
                   .registerCommandMonitor(c -> monitorTwo);
        final var result = testSubject.commandMonitor(config, CommandBus.class, null);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).contains(monitorOne, monitorTwo);
    }

    @Test
    void registeredCommandMonitorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        MessageMonitor<? super CommandMessage> testMonitor = commandMessageMonitor();
        MessageMonitorRegistry result = testSubject.registerCommandMonitor(c -> {
            builderInvocationCount.incrementAndGet();
            return testMonitor;
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        MessageMonitor<? super CommandMessage> commandMonitor =
                result.commandMonitor(config, CommandBus.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        commandMonitor = result.commandMonitor(config, CommandBus.class, "this-name");
        commandMonitor = result.commandMonitor(config, CommandBus.class, "that-name");
        assertThat(commandMonitor).isEqualTo(testMonitor);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeringCommandMonitorBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = CommandBus.class;
        String expectedName = "commandBusName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        final var monitor = commandMessageMonitor();

        testSubject.registerCommandMonitor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return monitor;
                }
        );

        final var result = testSubject.commandMonitor(config, expectedType, expectedName);
        assertThat(result).isEqualTo(monitor);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void registeringSingleEventMonitorResultsInRegisteredMonitor() {
        final var monitor = eventMessageMonitor();

        testSubject.registerEventMonitor(c -> monitor);
        final var result = testSubject.eventMonitor(config, EventSink.class, null);

        assertThat(result).isEqualTo(monitor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void registeringSeveralEventMonitorsResultsInMultiMessageMonitor() {
        final var monitorOne = eventMessageMonitor();
        final var monitorTwo = eventMessageMonitor();

        testSubject.registerEventMonitor(c -> monitorOne)
                   .registerEventMonitor(c -> monitorTwo);
        final var result = testSubject.eventMonitor(config, EventSink.class, null);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).contains(monitorOne, monitorTwo);
    }

    @Test
    void registeredEventMonitorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        MessageMonitor<? super EventMessage> testMonitor = eventMessageMonitor();
        MessageMonitorRegistry result = testSubject.registerEventMonitor(c -> {
            builderInvocationCount.incrementAndGet();
            return testMonitor;
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        MessageMonitor<? super EventMessage> eventMonitor =
                result.eventMonitor(config, EventSink.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        eventMonitor = result.eventMonitor(config, EventSink.class, "this-name");
        eventMonitor = result.eventMonitor(config, EventSink.class, "that-name");
        assertThat(eventMonitor).isEqualTo(testMonitor);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeringEventMonitorBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = EventSink.class;
        String expectedName = "eventSinkName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        final var monitor = eventMessageMonitor();

        testSubject.registerEventMonitor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return monitor;
                }
        );

        final var result = testSubject.eventMonitor(config, expectedType, expectedName);
        assertThat(result).isEqualTo(monitor);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void registeringSingleQueryMonitorResultsInRegisteredMonitor() {
        final var monitor = queryMessageMonitor();

        testSubject.registerQueryMonitor(c -> monitor);
        final var result = testSubject.queryMonitor(config, QueryBus.class, null);

        assertThat(result).isEqualTo(monitor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void registeringSeveralQueryMonitorsResultsInMultiMessageMonitor() {
        final var monitorOne = queryMessageMonitor();
        final var monitorTwo = queryMessageMonitor();

        testSubject.registerQueryMonitor(c -> monitorOne)
                   .registerQueryMonitor(c -> monitorTwo);
        final var result = testSubject.queryMonitor(config, QueryBus.class, null);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).contains(monitorOne, monitorTwo);
    }

    @Test
    void registeredQueryMonitorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        MessageMonitor<? super QueryMessage> testMonitor = queryMessageMonitor();
        MessageMonitorRegistry result = testSubject.registerQueryMonitor(c -> {
            builderInvocationCount.incrementAndGet();
            return testMonitor;
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        MessageMonitor<? super QueryMessage> queryMonitor =
                result.queryMonitor(config, QueryBus.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        queryMonitor = result.queryMonitor(config, QueryBus.class, "this-name");
        queryMonitor = result.queryMonitor(config, QueryBus.class, "that-name");
        assertThat(queryMonitor).isEqualTo(testMonitor);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeringQueryMonitorBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = QueryBus.class;
        String expectedName = "queryBusName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        final var monitor = queryMessageMonitor();

        testSubject.registerQueryMonitor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return monitor;
                }
        );

        final var result = testSubject.queryMonitor(config, expectedType, expectedName);
        assertThat(result).isEqualTo(monitor);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void registeringSingleSubscriptionQueryMonitorResultsInRegisteredMonitor() {
        final var monitor = subscriptionSubscriptionQueryMessageMonitor();

        testSubject.registerSubscriptionQueryUpdateMonitor(c -> monitor);
        final var result = testSubject.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);

        assertThat(result).isEqualTo(monitor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void registeringSeveralSubscriptionQueryMonitorsResultsInMultiMessageMonitor() {
        final var monitorOne = subscriptionSubscriptionQueryMessageMonitor();
        final var monitorTwo = subscriptionSubscriptionQueryMessageMonitor();

        testSubject.registerSubscriptionQueryUpdateMonitor(c -> monitorOne)
                   .registerSubscriptionQueryUpdateMonitor(c -> monitorTwo);
        final var result = testSubject.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);

        assertThat(result).isInstanceOf(MultiMessageMonitor.class);
        assertThat(((MultiMessageMonitor) result).messageMonitors()).contains(monitorOne, monitorTwo);
    }

    @Test
    void registeredSubscriptionQueryMonitorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        MessageMonitor<? super SubscriptionQueryUpdateMessage> testMonitor =
                subscriptionSubscriptionQueryMessageMonitor();
        MessageMonitorRegistry result = testSubject.registerSubscriptionQueryUpdateMonitor(c -> {
            builderInvocationCount.incrementAndGet();
            return testMonitor;
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryUpdateMonitor =
                result.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once, regardless of input.
        subscriptionQueryUpdateMonitor = result.subscriptionQueryUpdateMonitor(config, QueryBus.class, "this-name");
        subscriptionQueryUpdateMonitor = result.subscriptionQueryUpdateMonitor(config, QueryBus.class, "that-name");
        assertThat(subscriptionQueryUpdateMonitor).isEqualTo(testMonitor);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeringSubscriptionQueryMonitorBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = QueryBus.class;
        String expectedName = "queryBusName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        final var monitor = subscriptionSubscriptionQueryMessageMonitor();

        testSubject.registerSubscriptionQueryUpdateMonitor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return monitor;
                }
        );

        final var result = testSubject.subscriptionQueryUpdateMonitor(config, expectedType, expectedName);
        assertThat(result).isEqualTo(monitor);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void providesNoopMessageMonitorWhenNoMonitorsAreRegistered() {
        assertThat(testSubject.commandMonitor(config, CommandBus.class, null)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.eventMonitor(config, EventSink.class, null)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.queryMonitor(config, QueryBus.class, null)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.subscriptionQueryUpdateMonitor(config, QueryBus.class, null))
                .isInstanceOf(NoOpMessageMonitor.class);
    }

    @Test
    void providesNoopMessageMonitorWhenRegisteredMonitorsAreAllNoOpMessageMonitors() {
        // Registers several no-op monitors to ensure we do not collect no-op monitors in a multi-monitor
        testSubject.registerCommandMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerCommandMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerEventMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerEventMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerQueryMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerQueryMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerSubscriptionQueryUpdateMonitor(c -> NoOpMessageMonitor.INSTANCE)
                   .registerSubscriptionQueryUpdateMonitor(c -> NoOpMessageMonitor.INSTANCE);

        assertThat(testSubject.commandMonitor(config, CommandBus.class, null)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.eventMonitor(config, EventSink.class, null)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.queryMonitor(config, QueryBus.class, null)).isInstanceOf(NoOpMessageMonitor.class);
        assertThat(testSubject.subscriptionQueryUpdateMonitor(config, QueryBus.class, null))
                .isInstanceOf(NoOpMessageMonitor.class);
    }

    @Test
    void registersGenericMessageMonitorAsFourSpecializedMonitors() {
        MessageMonitor<Message> monitor = genericMessageMonitor();
        testSubject.registerMonitor(c -> monitor);

        MessageMonitor<? super CommandMessage> commandMonitor =
                testSubject.commandMonitor(config, CommandBus.class, null);
        assertThat(commandMonitor).isNotInstanceOf(NoOpMessageMonitor.class);

        MessageMonitor<? super EventMessage> eventMonitor = testSubject.eventMonitor(config, EventSink.class, null);
        assertThat(eventMonitor).isNotInstanceOf(NoOpMessageMonitor.class);

        MessageMonitor<? super QueryMessage> queryMonitor = testSubject.queryMonitor(config, QueryBus.class, null);
        assertThat(queryMonitor).isNotInstanceOf(NoOpMessageMonitor.class);

        MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryMonitor =
                testSubject.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);
        assertThat(subscriptionQueryMonitor).isNotInstanceOf(NoOpMessageMonitor.class);
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

        testSubject.commandMonitor(config, CommandBus.class, null).onMessageIngested(command).reportSuccess();
        testSubject.eventMonitor(config, EventSink.class, null).onMessageIngested(event).reportSuccess();
        testSubject.queryMonitor(config, QueryBus.class, null).onMessageIngested(query).reportSuccess();

        assertThat(capturedCommand).hasValue(command);
        assertThat(capturedEvent).hasValue(event);
        assertThat(capturedQuery).hasValue(query);
    }

    @Test
    void registeredGenericMonitorsAreOnlyConstructedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        MessageMonitorRegistry result = testSubject.registerMonitor(c -> {
            builderInvocationCount.incrementAndGet();
            return genericMessageMonitor();
        });

        result.commandMonitor(config, CommandBus.class, null);
        result.commandMonitor(config, CommandBus.class, null);
        result.eventMonitor(config, EventSink.class, null);
        result.eventMonitor(config, EventSink.class, null);
        result.queryMonitor(config, QueryBus.class, null);
        result.queryMonitor(config, QueryBus.class, null);
        result.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);
        result.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);

        assertThat(builderInvocationCount.get()).isEqualTo(1);
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

    private static MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionSubscriptionQueryMessageMonitor() {
        return (MessageMonitor<SubscriptionQueryUpdateMessage>) message -> NoOpMessageMonitorCallback.INSTANCE;
    }
}
