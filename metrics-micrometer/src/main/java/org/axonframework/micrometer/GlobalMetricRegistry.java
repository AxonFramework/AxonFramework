/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.config.Configurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MultiMessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.axonframework.micrometer.TagsUtil.*;

/**
 * Registry for application metrics with convenient ways to register Axon components.
 *
 * @author Rene de Waele
 * @author Marijn van Zelst
 * @since 4.1
 */
public class GlobalMetricRegistry {

    private static final Logger logger = LoggerFactory.getLogger(GlobalMetricRegistry.class);

    private static final String EVENT_PROCESSOR_METRICS_NAME = "eventProcessor";

    private final MeterRegistry registry;

    /**
     * Initializes a new {@link GlobalMetricRegistry} delegating to a new {@link MeterRegistry} with default settings.
     */
    public GlobalMetricRegistry() {
        this(new SimpleMeterRegistry());
    }

    /**
     * Initializes a {@link GlobalMetricRegistry} delegating to the given {@code meterRegistry}.
     *
     * @param meterRegistry the {@link MeterRegistry} which will record the metrics
     */
    public GlobalMetricRegistry(MeterRegistry meterRegistry) {
        this.registry = meterRegistry;
    }

    /**
     * Registers the {@link MeterRegistry} with the given {@code configurer} via {@link
     * Configurer#configureMessageMonitor(Function)}. Components registered by the {@link Configurer} will be added by
     * invocation of {@link #registerComponent(Class, String)}.
     *
     * @param configurer the application's {@link Configurer}
     * @return the {@link Configurer}, with the new registration applied, for chaining
     */
    @SuppressWarnings("unchecked")
    public Configurer registerWithConfigurer(Configurer configurer) {
        return configurer.configureMessageMonitor(
                configuration
                        -> (componentType, componentName)
                        -> (MessageMonitor<Message<?>>) registerComponent(componentType, componentName)
        );
    }

    /**
     * Registers the {@link MeterRegistry} with the given {@code configurer} via {@link
     * Configurer#configureMessageMonitor(Function)}. Components registered by the {@link Configurer} will be added by
     * invocation of {@link #registerComponentWithDefaultTags(Class, String)}.
     *
     * @param configurer the application's {@link Configurer}
     * @return the {@link Configurer}, with the new registration applied using {@link Tag}s, for chaining
     */
    @SuppressWarnings("unchecked")
    public Configurer registerWithConfigurerWithDefaultTags(Configurer configurer) {
        return configurer.configureMessageMonitor(
                configuration
                        -> (componentType, componentName)
                        -> (MessageMonitor<Message<?>>) registerComponentWithDefaultTags(componentType, componentName)
        );
    }

    /**
     * Registers new metrics to the registry to monitor a component of the given {@code componentType}. The monitor will
     * be registered with the {@link MeterRegistry} under the given {@code componentName}. The returned {@link
     * MessageMonitor} can be installed on the component to initiate the monitoring.
     *
     * @param componentType the type of component to register
     * @param componentName the name under which the component should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of a given {@code componentType}
     */
    public MessageMonitor<? extends Message<?>> registerComponent(Class<?> componentType, String componentName) {
        if (EventProcessor.class.isAssignableFrom(componentType)) {
            return registerEventProcessor(componentName);
        }
        if (CommandBus.class.isAssignableFrom(componentType)) {
            return registerCommandBus(componentName);
        }
        if (EventBus.class.isAssignableFrom(componentType)) {
            return registerEventBus(componentName);
        }
        if (QueryBus.class.isAssignableFrom(componentType)) {
            return registerQueryBus(componentName);
        }
        if (QueryUpdateEmitter.class.isAssignableFrom(componentType)) {
            return registerQueryUpdateEmitter(componentName);
        }
        logger.warn("Cannot provide MessageMonitor for component [{}] of type [{}]. Returning No-Op instance.",
                    componentName, componentType.getSimpleName());
        return NoOpMessageMonitor.instance();
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventProcessor}. The monitor will be registered with
     * the registry under the given {@code eventProcessorName}. The returned {@link MessageMonitor} can be installed on
     * the {@code EventProcessor} to initiate the monitoring.
     *
     * @param eventProcessorName the name under which the {@link EventProcessor} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventProcessor}
     */
    public MessageMonitor<? super EventMessage<?>> registerEventProcessor(String eventProcessorName) {
        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(MessageTimerMonitor.buildMonitor(eventProcessorName, registry));
        monitors.add(EventProcessorLatencyMonitor.builder()
                                                 .meterNamePrefix(eventProcessorName)
                                                 .meterRegistry(registry)
                                                 .build());
        monitors.add(CapacityMonitor.buildMonitor(eventProcessorName, registry));
        monitors.add(MessageCountingMonitor.buildMonitor(eventProcessorName, registry));
        return new MultiMessageMonitor<>(monitors);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link CommandBus}. The monitor will be registered with the
     * registry under the given {@code commandBusName}. The returned {@link MessageMonitor} can be installed on the
     * {@code CommandBus} to initiate the monitoring.
     *
     * @param commandBusName the name under which the {@link CommandBus} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link CommandBus}
     */
    public MessageMonitor<? super CommandMessage<?>> registerCommandBus(String commandBusName) {
        return registerDefaultHandlerMessageMonitor(commandBusName);
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventBus}. The monitor will be registered with the
     * registry under the given {@code eventBusName}. The returned {@link MessageMonitor} can be installed on the {@code
     * EventBus} to initiate the monitoring.
     *
     * @param eventBusName the name under which the {@link EventBus} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventBus}
     */
    public MessageMonitor<? super EventMessage<?>> registerEventBus(String eventBusName) {
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(eventBusName, registry);
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(eventBusName, registry);

        return new MultiMessageMonitor<>(Arrays.asList(messageCountingMonitor, messageTimerMonitor));
    }

    /**
     * Registers new metrics to the registry to monitor a {@link QueryBus}. The monitor will be registered with the
     * registry under the given {@code queryBusName}. The returned {@link MessageMonitor} can be installed on the {@code
     * QueryBus} to initiate the monitoring.
     *
     * @param queryBusName the name under which the {@link QueryBus} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link QueryBus}
     */
    public MessageMonitor<? super QueryMessage<?, ?>> registerQueryBus(String queryBusName) {
        return registerDefaultHandlerMessageMonitor(queryBusName);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link QueryUpdateEmitter}. The monitor will be registered
     * with the registry under the given {@code updateEmitterName}. The returned {@link MessageMonitor} can be installed
     * on the {@code QueryUpdateEmitter} to initiate the monitoring.
     *
     * @param updateEmitterName the name under which the {@link QueryUpdateEmitter} should be registered to the
     *                          registry
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link QueryUpdateEmitter}
     */
    private MessageMonitor<? extends Message<?>> registerQueryUpdateEmitter(String updateEmitterName) {
        return registerDefaultHandlerMessageMonitor(updateEmitterName);
    }

    /**
     * Registers new metrics to the registry to monitor a component of the given {@code componentType}. The monitor will
     * be registered with the registry under the given {@code componentName}, utilizing {@link Tag}s. The default set of
     * {@link Tag}s includes the 'message payload type' and additionally the 'processor name' for the {@link
     * EventProcessor} instances. The returned {@link MessageMonitor} can be installed on the component to initiate the
     * monitoring.
     *
     * @param componentType the type of component to register
     * @param componentName the name under which the component should be registered to the registry
     * @return a {@link MessageMonitor} with {@link Tag}s enabled to monitor the behavior of the given {@code
     * componentType}
     */
    public MessageMonitor<? extends Message<?>> registerComponentWithDefaultTags(Class<?> componentType,
                                                                                 String componentName) {
        if (EventProcessor.class.isAssignableFrom(componentType)) {
            return registerEventProcessor(
                    EVENT_PROCESSOR_METRICS_NAME,
                    message -> Tags.of(
                            PAYLOAD_TYPE_TAG, message.getPayloadType().getSimpleName(),
                            PROCESSOR_NAME_TAG, componentName
                    ),
                    message -> Tags.of(
                            PROCESSOR_NAME_TAG, componentName
                    )
            );
        }
        if (CommandBus.class.isAssignableFrom(componentType)) {
            return registerCommandBus(componentName, PAYLOAD_TYPE_TAGGER_FUNCTION);
        }
        if (EventBus.class.isAssignableFrom(componentType)) {
            return registerEventBus(componentName, PAYLOAD_TYPE_TAGGER_FUNCTION);
        }
        if (QueryBus.class.isAssignableFrom(componentType)) {
            return registerQueryBus(componentName, PAYLOAD_TYPE_TAGGER_FUNCTION);
        }
        if (QueryUpdateEmitter.class.isAssignableFrom(componentType)) {
            return registerQueryUpdateEmitter(componentName, PAYLOAD_TYPE_TAGGER_FUNCTION);
        }
        logger.warn("Cannot provide MessageMonitor for component [{}] of type [{}]. Returning No-Op instance.",
                    componentName, componentType.getSimpleName());
        return NoOpMessageMonitor.instance();
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventProcessor} using {@link Tag}s through the given
     * {@code tagsBuilder}. The monitor will be registered with the registry under the given {@code eventProcessorName}.
     * The returned {@link MessageMonitor} can be installed on the {@code EventProcessor} to initiate the monitoring.
     *
     * @param eventProcessorName the name under which the {@link EventProcessor} should be registered to the registry
     * @param tagsBuilder        the function used to construct the list of {@link Tag}, based on the ingested message
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventProcessor}
     * @deprecated Please use the {@link #registerEventProcessor(String, Function, Function)} instead, which has
     * separate tags for the latency metric.
     */
    @Deprecated
    public MessageMonitor<? super EventMessage<?>> registerEventProcessor(String eventProcessorName,
                                                                          Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return registerEventProcessor(eventProcessorName, tagsBuilder, tagsBuilder);
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventProcessor} using {@link Tag}s through the given
     * {@code tagsBuilder}. The monitor will be registered with the registry under the given {@code eventProcessorName}.
     * The returned {@link MessageMonitor} can be installed on the {@code EventProcessor} to initiate the monitoring.
     * <p>
     * The second tags builder parameter is specifically for the latency metric. These tags might be wanted to be less
     * specific than the others. For example, the {@link MessageTimerMonitor} makes sense per payload type, but the
     * latency makes sense only for the event processor as a whole.
     *
     * @param eventProcessorName the name under which the {@link EventProcessor} should be registered to the registry
     * @param tagsBuilder        the function used to construct the list of {@link Tag}, based on the ingested message
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventProcessor}
     */

    public MessageMonitor<? super EventMessage<?>> registerEventProcessor(String eventProcessorName,
                                                                          Function<Message<?>, Iterable<Tag>> tagsBuilder,
                                                                          Function<Message<?>, Iterable<Tag>> latencyTagsBuilder
                                                                          ) {
        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(MessageTimerMonitor.buildMonitor(eventProcessorName, registry, tagsBuilder));
        monitors.add(EventProcessorLatencyMonitor.builder()
                                                 .meterNamePrefix(eventProcessorName)
                                                 .meterRegistry(registry)
                                                 .tagsBuilder(latencyTagsBuilder)
                                                 .build());
        monitors.add(CapacityMonitor.buildMonitor(eventProcessorName, registry, tagsBuilder));
        monitors.add(MessageCountingMonitor.buildMonitor(eventProcessorName, registry, tagsBuilder));
        return new MultiMessageMonitor<>(monitors);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link CommandBus} using {@link Tag}s through the given {@code
     * tagsBuilder}. The monitor will be registered with the registry under the given {@code commandBusName}. The
     * returned {@link MessageMonitor} can be installed on the {@code CommandBus} to initiate the monitoring.
     *
     * @param commandBusName the name under which the {@link CommandBus} should be registered to the registry
     * @param tagsBuilder    the function used to construct the list of {@link Tag}, based on the ingested message
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link CommandBus}
     */
    public MessageMonitor<? super CommandMessage<?>> registerCommandBus(String commandBusName,
                                                                        Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return registerDefaultHandlerMessageMonitor(commandBusName, tagsBuilder);
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventBus} using {@link Tag}s through the given {@code
     * tagsBuilder}. The monitor will be registered with the registry under the given {@code eventBusName}. The returned
     * {@link MessageMonitor} can be installed on the {@code EventBus} to initiate the monitoring.
     *
     * @param eventBusName the name under which the {@link EventBus} should be registered to the registry
     * @param tagsBuilder  the function used to construct the list of {@link Tag}, based on the ingested message
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventBus}
     */
    public MessageMonitor<? super EventMessage<?>> registerEventBus(String eventBusName,
                                                                    Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(eventBusName, registry);
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(eventBusName, registry, tagsBuilder);

        return new MultiMessageMonitor<>(Arrays.asList(messageCountingMonitor, messageTimerMonitor));
    }

    /**
     * Registers new metrics to the registry to monitor a {@link QueryBus} using {@link Tag}s through the given {@code
     * tagsBuilder}. The monitor will be registered with the registry under the given {@code queryBusName}. The returned
     * {@link MessageMonitor} can be installed on the {@code QueryBus} to initiate the monitoring.
     *
     * @param queryBusName the name under which the {@link QueryBus} should be registered to the registry
     * @param tagsBuilder  the function used to construct the list of {@link Tag}, based on the ingested message
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link QueryBus}
     */
    public MessageMonitor<? super QueryMessage<?, ?>> registerQueryBus(String queryBusName,
                                                                       Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return registerDefaultHandlerMessageMonitor(queryBusName, tagsBuilder);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link QueryUpdateEmitter}, using {@link Tag}s through the
     * given {@code tagsBuilder}. The monitor will be registered with the registry under the given {@code
     * updateEmitterName}. The returned {@link MessageMonitor} can be installed on the {@code QueryUpdateEmitter} to
     * initiate the monitoring.
     *
     * @param updateEmitterName the name under which the {@link QueryUpdateEmitter} should be registered to the
     *                          registry
     * @param tagsBuilder       the function used to construct the list of {@link Tag}, based on the ingested message
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link QueryUpdateEmitter}
     */
    private MessageMonitor<? extends Message<?>> registerQueryUpdateEmitter(String updateEmitterName,
                                                                            Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return registerDefaultHandlerMessageMonitor(updateEmitterName, tagsBuilder);
    }

    private MessageMonitor<Message<?>> registerDefaultHandlerMessageMonitor(String name) {
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(name, registry);
        CapacityMonitor capacityMonitor = CapacityMonitor.buildMonitor(name, registry);
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(name, registry);

        return new MultiMessageMonitor<>(messageTimerMonitor, capacityMonitor, messageCountingMonitor);
    }

    private MessageMonitor<Message<?>> registerDefaultHandlerMessageMonitor(String name,
                                                                            Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(name, registry, tagsBuilder);
        CapacityMonitor capacityMonitor = CapacityMonitor.buildMonitor(name, registry, tagsBuilder);
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(name,
                                                                                            registry,
                                                                                            tagsBuilder);

        return new MultiMessageMonitor<>(messageTimerMonitor, capacityMonitor, messageCountingMonitor);
    }

    /**
     * Returns the global {@link MeterRegistry} to which components are registered.
     *
     * @return the global registry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
