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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Nonnull;
import org.axonframework.common.StringUtils;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.monitoring.configuration.MessageMonitorRegistry;
import org.axonframework.messaging.queryhandling.QueryBus;

import java.util.Objects;
import java.util.function.Function;

import static org.axonframework.extension.metrics.micrometer.TagsUtil.*;

/**
 * Implementation of the {@link ConfigurationEnhancer} that uses the {@link MeterRegistry} to decorate several
 * components with {@link MessageMonitor MessageMonitors} with unique {@code MetricRegistries} by registering
 * {@link org.axonframework.messaging.monitoring.configuration.MessageMonitorFactory MessageMonitorFactories} with the
 * {@link MessageMonitorRegistry}.
 * <p>
 * Components that are decorated are:
 * <ul>
 * <li>Any {@link CommandBus} implementation present in the {@link ComponentRegistry} receives a {@link MessageCountingMonitor), {@link MessageTimerMonitor}, and {@link CapacityMonitor}.</li>
 * <li>Any {@link EventSink} implementation present in the {@link ComponentRegistry} receives a {@code MessageCountingMonitor) and {@code MessageTimerMonitor}.</li>
 * <li>Any {@link EventProcessor} implementation present in the {@link ComponentRegistry} receives a {@code MessageCountingMonitor), {@code MessageTimerMonitor}, {@code CapacityMonitor}, and {@link EventProcessorLatencyMonitor}.</li>
 * <li>Any {@link QueryBus} implementation present in the {@link ComponentRegistry} receives a {@code MessageCountingMonitor), {@code MessageTimerMonitor}, and {@code CapacityMonitor}.</li>
 * </ul>
 * <p>
 * When constructing the {@code MetricsConfigurationEnhancer}, users can choose to add dimensionality to the
 * measurements. When enabled, Micrometer {@link Tag tags} are added by default for the
 * {@link Message#type() message type} {@link MessageType#name() name} and {@link EventProcessor} names, when
 * applicable.
 *
 * @author Steven van Beelen
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @since 4.1.0
 */
public class MetricsConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer, set to {@code -1024}.
     */
    public static final int ENHANCER_ORDER = -1024;

    private final MeterRegistry registry;
    private final boolean useDimensions;

    /**
     * Initializes a new {@code MetricsConfigurerModule} constructing {@link MessageMonitor MessageMonitors} that are
     * registered with a new {@link MeterRegistry} with default settings.
     * <p>
     * Will have dimensionality enabled, which will add {@link Tag tags} to measurements.
     */
    public MetricsConfigurationEnhancer() {
        this(new SimpleMeterRegistry());
    }

    /**
     * Initializes a new {@code MetricsConfigurerModule} constructing {@link MessageMonitor MessageMonitors} that are
     * registered with the given {@code registry}.
     * <p>
     * Will have dimensionality enabled, which will add {@link Tag tags} to measurements.
     *
     * @param registry the {@link MeterRegistry} which will record the metrics
     */
    public MetricsConfigurationEnhancer(@Nonnull MeterRegistry registry) {
        this(registry, true);
    }

    /**
     * Initializes a new {@code MetricsConfigurerModule} constructing {@link MessageMonitor MessageMonitors} that are
     * registered with the given {@code registry} and {@code useDimensions} toggle.
     *
     * @param registry      the {@link MeterRegistry} which will record the metrics
     * @param useDimensions when set to {@code true} will add {@link Tag tags} to measurements
     */
    public MetricsConfigurationEnhancer(@Nonnull MeterRegistry registry, boolean useDimensions) {
        this.registry = Objects.requireNonNull(registry, "The MeterRegistry must not be null.");
        this.useDimensions = useDimensions;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerCommandMonitor(
                        (c, componentType, componentName) -> {
                            if (CommandBus.class.isAssignableFrom(componentType)) {
                                return constructHandlerMonitor(resolveToMonitorName(componentType, componentName));
                            } else {
                                return NoOpMessageMonitor.instance();
                            }
                        }
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerEventMonitor(
                        (c, componentType, componentName) -> {
                            if (EventSink.class.isAssignableFrom(componentType)) {
                                return constructEventSinkMonitors(resolveToMonitorName(componentType, componentName));
                            } else {
                                return NoOpMessageMonitor.instance();
                            }
                        }
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerQueryMonitor(
                        (c, componentType, componentName) -> {
                            if (QueryBus.class.isAssignableFrom(componentType)) {
                                return constructHandlerMonitor(resolveToMonitorName(componentType, componentName));
                            } else {
                                return NoOpMessageMonitor.instance();
                            }
                        }
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerEventMonitor(
                        (c, componentType, componentName) -> {
                            if (EventProcessor.class.isAssignableFrom(componentType)) {
                                return constructEventProcessorMonitors(
                                        resolveToMonitorName(componentType, componentName)
                                );
                            } else {
                                return NoOpMessageMonitor.instance();
                            }
                        }
                )
        ).registerDecorator(
                MessageMonitorRegistry.class, 0,
                (config, name, delegate) -> delegate.registerSubscriptionQueryUpdateMonitor(
                        (c, componentType, componentName) -> {
                            if (QueryBus.class.isAssignableFrom(componentType)) {
                                return constructHandlerMonitor(
                                        resolveToMonitorName(componentType, componentName) + "-emitter"
                                );
                            } else {
                                return NoOpMessageMonitor.instance();
                            }
                        }
                )
        );
    }

    private MessageMonitor<? super EventMessage> constructEventSinkMonitors(String sinkName) {
        MessageCountingMonitor countingMonitor = MessageCountingMonitor.buildMonitor(sinkName, registry);
        MessageTimerMonitor timerMonitor = MessageTimerMonitor.builder()
                                                              .meterNamePrefix(sinkName)
                                                              .meterRegistry(registry)
                                                              .tagsBuilder(useDimensions ? MESSAGE_TYPE_TAGGER : NO_OP)
                                                              .build();
        return new MultiMessageMonitor<>(countingMonitor, timerMonitor);
    }

    private MessageMonitor<? super EventMessage> constructEventProcessorMonitors(String processorName) {
        Function<Message, Iterable<Tag>> tagsBuilder = useDimensions ? processorTags(processorName) : NO_OP;
        MessageCountingMonitor countingMonitor =
                MessageCountingMonitor.buildMonitor(processorName, registry, tagsBuilder);
        MessageTimerMonitor timerMonitor = MessageTimerMonitor.builder()
                                                              .meterNamePrefix(processorName)
                                                              .meterRegistry(registry)
                                                              .tagsBuilder(tagsBuilder)
                                                              .build();
        CapacityMonitor capacityMonitor = CapacityMonitor.buildMonitor(processorName, registry, tagsBuilder);
        EventProcessorLatencyMonitor latencyMonitor =
                EventProcessorLatencyMonitor.builder()
                                            .meterNamePrefix(processorName)
                                            .meterRegistry(registry)
                                            .tagsBuilder(useDimensions ? processorNameTag(processorName) : NO_OP)
                                            .build();
        return new MultiMessageMonitor<>(countingMonitor, timerMonitor, capacityMonitor, latencyMonitor);
    }

    private MessageMonitor<Message> constructHandlerMonitor(String name) {
        Function<Message, Iterable<Tag>> tagsBuilder = useDimensions ? MESSAGE_TYPE_TAGGER : NO_OP;
        MessageCountingMonitor countingMonitor =
                MessageCountingMonitor.buildMonitor(name, registry, tagsBuilder);
        MessageTimerMonitor timerMonitor = MessageTimerMonitor.builder()
                                                              .meterNamePrefix(name)
                                                              .meterRegistry(registry)
                                                              .tagsBuilder(tagsBuilder)
                                                              .build();
        CapacityMonitor capacityMonitor = CapacityMonitor.buildMonitor(name, registry, tagsBuilder);
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
