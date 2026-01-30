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
import jakarta.annotation.Nonnull;
import org.axonframework.common.StringUtils;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.monitoring.configuration.MessageMonitorRegistry;
import org.axonframework.messaging.queryhandling.QueryBus;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the {@link ConfigurationEnhancer} that uses the {@link MetricRegistry} to decorate several
 * components with {@link MessageMonitor MessageMonitors} with unique {@code MetricRegistries} by registering
 * {@link org.axonframework.messaging.monitoring.configuration.MessageMonitorBuilder MessageMonitorBuilders} with the
 * {@link MessageMonitorRegistry}.
 * <p>
 * Components that are decorated are:
 * <ul>
 * <li>Any {@link CommandBus} implementation present in the {@link ComponentRegistry} receives a {@link MessageCountingMonitor), {@link MessageTimerMonitor}, and {@link CapacityMonitor}.</li>
 * <li>Any {@link EventSink} implementation present in the {@link ComponentRegistry} receives a {@code MessageCountingMonitor) and {@code MessageTimerMonitor}.</li>
 * <li>Any {@link EventProcessor} implementation present in the {@link ComponentRegistry} receives a {@code MessageCountingMonitor), {@code MessageTimerMonitor}, {@code CapacityMonitor}, and {@link EventProcessorLatencyMonitor}.</li>
 * <li>Any {@link QueryBus} implementation present in the {@link ComponentRegistry} receives a {@code MessageCountingMonitor), {@code MessageTimerMonitor}, and {@code CapacityMonitor}.</li>
 * </ul>
 *
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.2.0
 */
public class MetricsConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer, set to {@code -1024}.
     */
    public static final int ENHANCER_ORDER = -1024;

    private final MetricRegistry registry;

    /**
     * Initializes a new {@code MetricsConfigurerModule} constructing {@link MessageMonitor MessageMonitors} that are
     * registered with a new {@link MetricRegistry} with default settings.
     */
    public MetricsConfigurationEnhancer() {
        this(new MetricRegistry());
    }

    /**
     * Initializes a new {@code MetricsConfigurerModule} constructing {@link MessageMonitor MessageMonitors} that are
     * registered with the given {@code registry}.
     *
     * @param registry the {@link MetricRegistry} which will record the metrics
     */
    public MetricsConfigurationEnhancer(@Nonnull MetricRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "The MetricRegistry must not be null.");
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerCommandMonitor(
                        (c, componentType, componentName) -> componentType.isAssignableFrom(CommandBus.class)
                                ? constructHandlerMonitor(resolveToMonitorName(componentType, componentName))
                                : NoOpMessageMonitor.instance()
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerEventMonitor(
                        (c, componentType, componentName) -> componentType.isAssignableFrom(EventSink.class)
                                ? constructEventSinkMonitors(resolveToMonitorName(componentType, componentName))
                                : NoOpMessageMonitor.instance()
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerQueryMonitor(
                        (c, componentType, componentName) -> componentType.isAssignableFrom(QueryBus.class)
                                ? constructHandlerMonitor(resolveToMonitorName(componentType, componentName))
                                : NoOpMessageMonitor.instance()
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerEventMonitor(
                        (c, componentType, componentName) -> componentType.isAssignableFrom(EventProcessor.class)
                                ? constructEventProcessorMonitors(resolveToMonitorName(componentType, componentName))
                                : NoOpMessageMonitor.instance()
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerSubscriptionQueryUpdateMonitor(
                        (c, componentType, componentName) -> componentType.isAssignableFrom(QueryBus.class)
                                ? constructHandlerMonitor(resolveToMonitorName(componentType, componentName) + "-emitter")
                                : NoOpMessageMonitor.instance()
                )
        );
    }

    private MessageMonitor<? super EventMessage> constructEventSinkMonitors(String sinkName) {
        MessageCountingMonitor countingMonitor = new MessageCountingMonitor();
        MessageTimerMonitor timerMonitor = MessageTimerMonitor.builder().build();

        MetricRegistry eventSinkRegistry = new MetricRegistry();
        eventSinkRegistry.register("messageCounter", countingMonitor);
        eventSinkRegistry.register("messageTimer", timerMonitor);
        registry.register(sinkName, eventSinkRegistry);

        return new MultiMessageMonitor<>(countingMonitor, timerMonitor);
    }

    private MessageMonitor<? super EventMessage> constructEventProcessorMonitors(String processorName) {
        MessageCountingMonitor countingMonitor = new MessageCountingMonitor();
        MessageTimerMonitor timerMonitor = MessageTimerMonitor.builder().build();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);
        EventProcessorLatencyMonitor latencyMonitor = new EventProcessorLatencyMonitor();

        MetricRegistry eventProcessingRegistry = new MetricRegistry();
        eventProcessingRegistry.register("messageCounter", countingMonitor);
        eventProcessingRegistry.register("messageTimer", timerMonitor);
        eventProcessingRegistry.register("capacity", capacityMonitor);
        eventProcessingRegistry.register("latency", latencyMonitor);
        registry.register(processorName, eventProcessingRegistry);

        return new MultiMessageMonitor<>(countingMonitor, timerMonitor, capacityMonitor, latencyMonitor);
    }

    private MessageMonitor<Message> constructHandlerMonitor(String name) {
        MessageCountingMonitor countingMonitor = new MessageCountingMonitor();
        MessageTimerMonitor timerMonitor = MessageTimerMonitor.builder().build();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);

        MetricRegistry handlerRegistry = new MetricRegistry();
        handlerRegistry.register("messageCounter", countingMonitor);
        handlerRegistry.register("messageTimer", timerMonitor);
        handlerRegistry.register("capacity", capacityMonitor);
        registry.register(name, handlerRegistry);

        return new MultiMessageMonitor<>(countingMonitor, timerMonitor, capacityMonitor);
    }

    private static String resolveToMonitorName(Class<?> componentType, String componentName) {
        return StringUtils.emptyOrNull(componentName) ? componentType.getSimpleName() : componentName;
    }

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }
}
