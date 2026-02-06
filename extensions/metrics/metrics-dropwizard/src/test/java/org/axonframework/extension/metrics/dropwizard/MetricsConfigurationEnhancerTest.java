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

package org.axonframework.extension.metrics.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.common.util.StubLifecycleRegistry;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.configuration.DefaultMessageMonitorRegistry;
import org.axonframework.messaging.monitoring.configuration.MessageMonitorRegistry;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link MetricsConfigurationEnhancer}.
 *
 * @author Martijn Zelst
 * @author Steven van Beelen
 */
class MetricsConfigurationEnhancerTest {

    @Test
    void orderIsAsExpected() {
        MetricsConfigurationEnhancer testSubject = new MetricsConfigurationEnhancer();
        assertThat(testSubject.order()).isEqualTo(MetricsConfigurationEnhancer.ENHANCER_ORDER);
    }

    @Test
    void registersExpectedCommandBusMonitor() {
        Configuration config = buildDefaultConfig();

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super CommandMessage> commandMonitor =
                monitorRegistry.commandMonitor(config, CommandBus.class, null);
        assertThat(commandMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super CommandMessage>> commandMonitors =
                (List<MessageMonitor<? super CommandMessage>>) ((MultiMessageMonitor) commandMonitor).messageMonitors();
        assertThat(commandMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                              MessageTimerMonitor.class,
                                                              CapacityMonitor.class);
    }

    @Test
    void registersExpectedEventSinkMonitor() {
        Configuration config = buildDefaultConfig();

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super EventMessage> eventMonitor =
                monitorRegistry.eventMonitor(config, EventSink.class, null);
        assertThat(eventMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super EventMessage>> eventMonitors =
                (List<MessageMonitor<? super EventMessage>>) ((MultiMessageMonitor) eventMonitor).messageMonitors();
        assertThat(eventMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class, MessageTimerMonitor.class);
    }

    @Test
    void registersExpectedQueryBusMonitor() {
        Configuration config = buildDefaultConfig();

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super QueryMessage> queryMonitor =
                monitorRegistry.queryMonitor(config, QueryBus.class, null);
        assertThat(queryMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super QueryMessage>> queryMonitors =
                (List<MessageMonitor<? super QueryMessage>>) ((MultiMessageMonitor) queryMonitor).messageMonitors();
        assertThat(queryMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                            MessageTimerMonitor.class,
                                                            CapacityMonitor.class);
    }

    @Test
    void registersExpectedEventProcessorMonitor() {
        Configuration config = buildDefaultConfig();

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super EventMessage> eventMonitor =
                monitorRegistry.eventMonitor(config, EventProcessor.class, "testProcessor");
        assertThat(eventMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super EventMessage>> eventMonitors =
                (List<MessageMonitor<? super EventMessage>>) ((MultiMessageMonitor) eventMonitor).messageMonitors();
        assertThat(eventMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                            MessageTimerMonitor.class,
                                                            CapacityMonitor.class,
                                                            EventProcessorLatencyMonitor.class);
    }

    @Test
    void registersExpectedSubscriptionQueryUpdateMonitor() {
        Configuration config = buildDefaultConfig();

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super SubscriptionQueryUpdateMessage> queryMonitor =
                monitorRegistry.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);
        assertThat(queryMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super SubscriptionQueryUpdateMessage>> queryMonitors =
                (List<MessageMonitor<? super SubscriptionQueryUpdateMessage>>) ((MultiMessageMonitor) queryMonitor).messageMonitors();
        assertThat(queryMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                            MessageTimerMonitor.class,
                                                            CapacityMonitor.class);
    }

    @Test
    void registersExpectedCommandBusMonitorWithGivenMetricRegistry() {
        MetricRegistry metricRegistry = new MetricRegistry();
        Configuration config = buildConfig(metricRegistry);

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super CommandMessage> commandMonitor =
                monitorRegistry.commandMonitor(config, CommandBus.class, null);
        assertThat(commandMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super CommandMessage>> commandMonitors =
                (List<MessageMonitor<? super CommandMessage>>) ((MultiMessageMonitor) commandMonitor).messageMonitors();
        assertThat(commandMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                              MessageTimerMonitor.class,
                                                              CapacityMonitor.class);

        Optional<String> componentName = monitoredComponentName(metricRegistry);
        assertThat(componentName).isPresent();
        assertThat(componentName).contains(CommandBus.class.getSimpleName());
        List<String> uniqueMetricNames = retrieveUniqueMetricNames(metricRegistry);
        assertThat(uniqueMetricNames.size()).isEqualTo(3);
        assertThat(uniqueMetricNames).contains("messageCounter", "messageTimer", "capacity");
    }

    @Test
    void registersExpectedEventSinkMonitorWithGivenMetricRegistry() {
        MetricRegistry metricRegistry = new MetricRegistry();
        Configuration config = buildConfig(metricRegistry);

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super EventMessage> eventMonitor =
                monitorRegistry.eventMonitor(config, EventSink.class, null);
        assertThat(eventMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super EventMessage>> eventMonitors =
                (List<MessageMonitor<? super EventMessage>>) ((MultiMessageMonitor) eventMonitor).messageMonitors();
        assertThat(eventMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class, MessageTimerMonitor.class);

        Optional<String> componentName = monitoredComponentName(metricRegistry);
        assertThat(componentName).isPresent();
        assertThat(componentName).contains(EventSink.class.getSimpleName());
        List<String> uniqueMetricNames = retrieveUniqueMetricNames(metricRegistry);
        assertThat(uniqueMetricNames.size()).isEqualTo(2);
        assertThat(uniqueMetricNames).contains("messageCounter", "messageTimer");
    }

    @Test
    void registersExpectedQueryBusMonitorWithGivenMetricRegistry() {
        String expectedComponentName = "myQueryBus";
        MetricRegistry metricRegistry = new MetricRegistry();
        Configuration config = buildConfig(metricRegistry);

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super QueryMessage> queryMonitor =
                monitorRegistry.queryMonitor(config, QueryBus.class, expectedComponentName);
        assertThat(queryMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super QueryMessage>> queryMonitors =
                (List<MessageMonitor<? super QueryMessage>>) ((MultiMessageMonitor) queryMonitor).messageMonitors();
        assertThat(queryMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                            MessageTimerMonitor.class,
                                                            CapacityMonitor.class);

        Optional<String> componentName = monitoredComponentName(metricRegistry);
        assertThat(componentName).isPresent();
        assertThat(componentName).contains(expectedComponentName);
        List<String> uniqueMetricNames = retrieveUniqueMetricNames(metricRegistry);
        assertThat(uniqueMetricNames.size()).isEqualTo(3);
        assertThat(uniqueMetricNames).contains("messageCounter", "messageTimer", "capacity");
    }

    @Test
    void registersExpectedEventProcessorMonitorWithGivenMetricRegistry() {
        String expectedComponentName = "testProcessor";
        MetricRegistry metricRegistry = new MetricRegistry();
        Configuration config = buildConfig(metricRegistry);

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super EventMessage> eventMonitor =
                monitorRegistry.eventMonitor(config, EventProcessor.class, expectedComponentName);
        assertThat(eventMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super EventMessage>> eventMonitors =
                (List<MessageMonitor<? super EventMessage>>) ((MultiMessageMonitor) eventMonitor).messageMonitors();
        assertThat(eventMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                            MessageTimerMonitor.class,
                                                            CapacityMonitor.class,
                                                            EventProcessorLatencyMonitor.class);

        Optional<String> componentName = monitoredComponentName(metricRegistry);
        assertThat(componentName).isPresent();
        assertThat(componentName).contains(expectedComponentName);
        List<String> uniqueMetricNames = retrieveUniqueMetricNames(metricRegistry);
        assertThat(uniqueMetricNames.size()).isEqualTo(4);
        assertThat(uniqueMetricNames).contains("messageCounter", "messageTimer", "capacity", "latency");
    }

    @Test
    void registersExpectedSubscriptionQueryUpdateMonitorWithGivenMetricRegistry() {
        MetricRegistry metricRegistry = new MetricRegistry();
        Configuration config = buildConfig(metricRegistry);

        MessageMonitorRegistry monitorRegistry = config.getComponent(MessageMonitorRegistry.class);

        MessageMonitor<? super SubscriptionQueryUpdateMessage> queryMonitor =
                monitorRegistry.subscriptionQueryUpdateMonitor(config, QueryBus.class, null);
        assertThat(queryMonitor).isInstanceOf(MultiMessageMonitor.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MessageMonitor<? super SubscriptionQueryUpdateMessage>> queryMonitors =
                (List<MessageMonitor<? super SubscriptionQueryUpdateMessage>>) ((MultiMessageMonitor) queryMonitor).messageMonitors();
        assertThat(queryMonitors).hasExactlyElementsOfTypes(MessageCountingMonitor.class,
                                                            MessageTimerMonitor.class,
                                                            CapacityMonitor.class);

        Optional<String> componentName = monitoredComponentName(metricRegistry);
        assertThat(componentName).isPresent();
        assertThat(componentName).contains(QueryBus.class.getSimpleName() + "-emitter");
        List<String> uniqueMetricNames = retrieveUniqueMetricNames(metricRegistry);
        assertThat(uniqueMetricNames.size()).isEqualTo(3);
        assertThat(uniqueMetricNames).contains("messageCounter", "messageTimer", "capacity");
    }

    private Configuration buildDefaultConfig() {
        return buildForEnhancer(new MetricsConfigurationEnhancer());
    }

    private Configuration buildConfig(MetricRegistry metricRegistry) {
        return buildForEnhancer(new MetricsConfigurationEnhancer(metricRegistry));
    }

    private static Configuration buildForEnhancer(MetricsConfigurationEnhancer testSubject) {
        DefaultComponentRegistry registry = new DefaultComponentRegistry();
        registry.disableEnhancerScanning();
        registry.registerEnhancer(testSubject);
        registry.registerComponent(MessageMonitorRegistry.class, c -> new DefaultMessageMonitorRegistry());
        return registry.build(new StubLifecycleRegistry());
    }

    private static Optional<String> monitoredComponentName(MetricRegistry registry) {
        return registry.getMetrics()
                       .keySet()
                       .stream()
                       .findFirst()
                       .map(metricName -> metricName.split("\\.", 3)[0]);
    }

    private static List<String> retrieveUniqueMetricNames(MetricRegistry registry) {
        return registry.getMetrics()
                       .keySet()
                       .stream()
                       .map(metricName -> metricName.split("\\.", 3)[1])
                       .distinct()
                       .toList();
    }
}
