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

package org.axonframework.messaging.core.configuration;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitorCallback;
import org.axonframework.messaging.monitoring.configuration.MessageMonitorRegistry;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class specific for the {@link MessageMonitor} registration methods on the {@link MessagingConfigurer}.
 *
 * @author Steven van Beelen
 */
class MonitorRegistrationWithMessagingConfigurerTest {

    private MessagingConfigurer testSubject;

    @BeforeEach
    void setUp() {
        testSubject = MessagingConfigurer.create();
    }

    @Test
    void registerMessageMonitorMakesMonitorRetrievableThroughTheMonitorRegistryForAllTypes() {
        AtomicInteger counter = new AtomicInteger();
        MessageMonitor<Message> monitor = message -> {
            counter.incrementAndGet();
            return NoOpMessageMonitorCallback.INSTANCE;
        };

        Configuration result = testSubject.registerMessageMonitor(c -> monitor)
                                          .build();
        MessageMonitorRegistry monitorRegistry = result.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super CommandMessage> commandMonitor =
                monitorRegistry.commandMonitor(result, CommandBus.class, null);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        commandMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(1);

        MessageMonitor<? super EventMessage> eventMonitor =
                monitorRegistry.eventMonitor(result, EventSink.class, null);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        eventMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(2);

        MessageMonitor<? super QueryMessage> queryMonitor =
                monitorRegistry.queryMonitor(result, QueryBus.class, null);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        queryMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(3);

        MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryMonitor =
                monitorRegistry.subscriptionQueryUpdateMonitor(result, QueryBus.class, null);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        subscriptionQueryMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(4);
    }

    @Test
    void registerMessageMonitorBuilderMakesMonitorRetrievableThroughTheMonitorRegistryForTheExpectedTypeAndName() {
        AtomicInteger counter = new AtomicInteger();
        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        MessageMonitor<Message> monitor = message -> {
            counter.incrementAndGet();
            return NoOpMessageMonitorCallback.INSTANCE;
        };

        Configuration result = testSubject.registerMessageMonitor((config, componentType, componentName) -> {
                                              givenType.set(componentType);
                                              givenName.set(componentName);
                                              return monitor;
                                          })
                                          .build();
        MessageMonitorRegistry monitorRegistry = result.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super CommandMessage> commandMonitor =
                monitorRegistry.commandMonitor(result, CommandBus.class, "myCommandBus");
        //noinspection DataFlowIssue | Input is not important to validate invocation
        commandMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(1);
        assertThat(givenType).hasValue(CommandBus.class);
        assertThat(givenName).hasValue("myCommandBus");

        MessageMonitor<? super EventMessage> eventMonitor =
                monitorRegistry.eventMonitor(result, EventSink.class, "myEventSink");
        //noinspection DataFlowIssue | Input is not important to validate invocation
        eventMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(2);
        assertThat(givenType).hasValue(EventSink.class);
        assertThat(givenName).hasValue("myEventSink");

        MessageMonitor<? super QueryMessage> queryMonitor =
                monitorRegistry.queryMonitor(result, QueryBus.class, "myQueryBus");
        //noinspection DataFlowIssue | Input is not important to validate invocation
        queryMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(3);
        assertThat(givenType).hasValue(QueryBus.class);
        assertThat(givenName).hasValue("myQueryBus");

        MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryMonitor =
                monitorRegistry.subscriptionQueryUpdateMonitor(result, QueryBus.class, "myQueryBus");
        //noinspection DataFlowIssue | Input is not important to validate invocation
        subscriptionQueryMonitor.onMessageIngested(null);
        assertThat(counter).hasValue(4);
        assertThat(givenType).hasValue(QueryBus.class);
        assertThat(givenName).hasValue("myQueryBus");
    }

    @Test
    void registerCommandMonitorMakesMonitorRetrievableThroughTheMonitorRegistry() {
        //noinspection unchecked
        MessageMonitor<CommandMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerCommandMonitor(c -> monitor)
                                          .build();

        MessageMonitor<? super CommandMessage> commandMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .commandMonitor(result, CommandBus.class, null);
        assertThat(commandMonitor).isEqualTo(monitor);
    }

    @Test
    void registerCommandMonitorBuilderMakesMonitorRetrievableThroughTheMonitorRegistry() {
        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        //noinspection unchecked
        MessageMonitor<CommandMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerCommandMonitor((config, componentType, componentName) -> {
                                              givenType.set(componentType);
                                              givenName.set(componentName);
                                              return monitor;
                                          })
                                          .build();

        MessageMonitor<? super CommandMessage> commandMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .commandMonitor(result, CommandBus.class, "myCommandBus");
        assertThat(commandMonitor).isEqualTo(monitor);
        assertThat(givenType).hasValue(CommandBus.class);
        assertThat(givenName).hasValue("myCommandBus");
    }

    @Test
    void registerEventMonitorMakesMonitorRetrievableThroughTheMonitorRegistry() {
        //noinspection unchecked
        MessageMonitor<EventMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerEventMonitor(c -> monitor)
                                          .build();

        MessageMonitor<? super EventMessage> eventMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .eventMonitor(result, EventSink.class, null);
        assertThat(eventMonitor).isEqualTo(monitor);
    }

    @Test
    void registerEventMonitorBuilderMakesMonitorRetrievableThroughTheMonitorRegistry() {
        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        //noinspection unchecked
        MessageMonitor<EventMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerEventMonitor((config, componentType, componentName) -> {
                                              givenType.set(componentType);
                                              givenName.set(componentName);
                                              return monitor;
                                          })
                                          .build();

        MessageMonitor<? super EventMessage> eventMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .eventMonitor(result, EventSink.class, "myEventSink");
        assertThat(eventMonitor).isEqualTo(monitor);
        assertThat(givenType).hasValue(EventSink.class);
        assertThat(givenName).hasValue("myEventSink");
    }

    @Test
    void registerQueryMonitorMakesMonitorRetrievableThroughTheMonitorRegistry() {
        //noinspection unchecked
        MessageMonitor<QueryMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerQueryMonitor(c -> monitor)
                                          .build();

        MessageMonitor<? super QueryMessage> queryMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .queryMonitor(result, QueryBus.class, null);
        assertThat(queryMonitor).isEqualTo(monitor);
    }

    @Test
    void registerQueryMonitorBuilderMakesMonitorRetrievableThroughTheMonitorRegistry() {
        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        //noinspection unchecked
        MessageMonitor<QueryMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerQueryMonitor((config, componentType, componentName) -> {
                                              givenType.set(componentType);
                                              givenName.set(componentName);
                                              return monitor;
                                          })
                                          .build();

        MessageMonitor<? super QueryMessage> queryMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .queryMonitor(result, QueryBus.class, "myQueryBus");
        assertThat(queryMonitor).isEqualTo(monitor);
        assertThat(givenType).hasValue(QueryBus.class);
        assertThat(givenName).hasValue("myQueryBus");
    }

    @Test
    void registerSubscriptionQueryMonitorMakesMonitorRetrievableThroughTheMonitorRegistry() {
        //noinspection unchecked
        MessageMonitor<SubscriptionQueryUpdateMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerSubscriptionQueryUpdateMonitor(c -> monitor)
                                          .build();

        MessageMonitor<? super SubscriptionQueryUpdateMessage> queryMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .subscriptionQueryUpdateMonitor(result, QueryBus.class, null);
        assertThat(queryMonitor).isEqualTo(monitor);
    }

    @Test
    void registerSubscriptionQueryMonitorBuilderMakesMonitorRetrievableThroughTheMonitorRegistry() {
        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        //noinspection unchecked
        MessageMonitor<SubscriptionQueryUpdateMessage> monitor = mock(MessageMonitor.class);

        Configuration result = testSubject.registerSubscriptionQueryUpdateMonitor((config, componentType, componentName) -> {
                                              givenType.set(componentType);
                                              givenName.set(componentName);
                                              return monitor;
                                          })
                                          .build();

        MessageMonitor<? super SubscriptionQueryUpdateMessage> queryMonitor =
                result.getComponent(MessageMonitorRegistry.class)
                      .subscriptionQueryUpdateMonitor(result, QueryBus.class, "myQueryBus");
        assertThat(queryMonitor).isEqualTo(monitor);
        assertThat(givenType).hasValue(QueryBus.class);
        assertThat(givenName).hasValue("myQueryBus");
    }
}