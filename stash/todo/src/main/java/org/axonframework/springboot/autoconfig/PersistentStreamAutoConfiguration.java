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

package org.axonframework.springboot.autoconfig;


import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.DefaultPersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamEventSourceDefinition;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamScheduledExecutorBuilder;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessor;
import org.axonframework.extension.springboot.EventProcessorProperties;
import org.axonframework.extension.springboot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.extension.springboot.util.ConditionalOnQualifiedBean;
import org.axonframework.messaging.core.sequencing.SequencingPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Base Axon Server Autoconfiguration.
 * <p>
 * Constructs the {@link AxonServerConfiguration}, allowing for further configuration of Axon Server components through
 * property files or complete disablement of Axon Server.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 * TODO #3520 - Ensure this autoconfiguration works as intended!
 */
@AutoConfiguration
public class PersistentStreamAutoConfiguration {

    /**
     * Creates a {@link PersistentStreamScheduledExecutorBuilder} that constructs
     * {@link ScheduledExecutorService ScheduledExecutorServices} for each persistent stream. Defaults to a
     * {@link PersistentStreamScheduledExecutorBuilder#defaultFactory()}.
     *
     * @return The a {@link PersistentStreamScheduledExecutorBuilder} that constructs
     * {@link ScheduledExecutorService ScheduledExecutorServices} for each persistent stream.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingQualifiedBean(
            beanClass = ScheduledExecutorService.class,
            qualifier = "persistentStreamScheduler"
    )
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public PersistentStreamScheduledExecutorBuilder persistentStreamScheduledExecutorBuilder() {
        return PersistentStreamScheduledExecutorBuilder.defaultFactory();
    }

    /**
     * Creates a {@link PersistentStreamScheduledExecutorBuilder} defaulting to the same given
     * {@code persistentStreamScheduler} on each invocation. This bean-creation method is in place for backwards
     * compatibility with 4.10.0, which defaulted to this behavior based on a bean of type
     * {@link ScheduledExecutorService} with qualified {@code persistentStreamScheduler}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnQualifiedBean(
            beanClass = ScheduledExecutorService.class,
            qualifier = "persistentStreamScheduler"
    )
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public PersistentStreamScheduledExecutorBuilder backwardsCompatiblePersistentStreamScheduledExecutorBuilder(
            @Qualifier("persistentStreamScheduler") ScheduledExecutorService persistentStreamScheduler
    ) {
        return (threadCount, streamName) -> persistentStreamScheduler;
    }

    /**
     * Constructs a {@link PersistentStreamMessageSourceRegistrar} to create and register Spring beans for persistent
     * streams.
     *
     * @param environment     The Spring {@link Environment}.
     * @param executorBuilder The {@link PersistentStreamScheduledExecutorBuilder} used to construct a
     *                        {@link ScheduledExecutorService} to perform the persistent stream's tasks with.
     * @return The {@link PersistentStreamMessageSourceRegistrar} to create and register Spring beans for persistent
     * streams.
     */
    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public PersistentStreamMessageSourceRegistrar persistentStreamRegistrar(
            Environment environment,
            PersistentStreamScheduledExecutorBuilder executorBuilder
    ) {
        return new PersistentStreamMessageSourceRegistrar(environment, executorBuilder);
    }

    /**
     * Creates a {@code ConfigurerModule} to invoke {@code EventProcessingConfigurer::usingSubscribingEventProcessors}
     *
     * @param executorBuilder         The {@link ScheduledExecutorService} builder used during construction of the
     *                                {@link PersistentStreamEventSourceDefinition}.
     * @param psFactory               used during construction of the {@link PersistentStreamEventSourceDefinition}.
     * @param axonServerConfiguration Contains the persistent stream settings.
     * @return A {@code ConfigurerModule} to configure
     */
    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.auto-persistent-streams-enable")
    public ConfigurationEnhancer autoPersistentStreamMessageSourceDefinitionBuilder(
            PersistentStreamScheduledExecutorBuilder executorBuilder,
            PersistentStreamMessageSourceFactory psFactory,
            AxonServerConfiguration axonServerConfiguration) {
//        AxonServerConfiguration.PersistentStreamSettings psSettings = axonServerConfiguration.getAutoPersistentStreamsSettings();
//        return configurer -> configurer.eventProcessing().usingSubscribingEventProcessors(
//                processingGroupName -> {
//                    String psName = processingGroupName + "-stream";
//                    return new PersistentStreamMessageSourceDefinition(
//                            processingGroupName,
//                            new PersistentStreamProperties(psName,
//                                                           psSettings.getInitialSegmentCount(),
//                                                           psSettings.getSequencingPolicy(),
//                                                           psSettings.getSequencingPolicyParameters(),
//                                                           psSettings.getInitialPosition(),
//                                                           psSettings.getFilter()),
//                            executorBuilder.build(psSettings.getThreadCount(), psName),
//                            psSettings.getBatchSize(),
//                            null,
//                            psFactory
//                    );
//                });
        return componentRegistry -> {
        };
    }

    /**
     * Creates a bean of type {@link PersistentStreamMessageSourceFactory} if one is not already defined. This factory
     * is used to create instances of the {@link PersistentStreamMessageSource} with specified configurations.
     * <p>
     * The returned factory creates a new {@link PersistentStreamMessageSource} with the following parameters:
     * <ul>
     *     <li>{@code name}: The name of the persistent stream.</li>
     *     <li>{@code configuration}: The Axon framework configuration.</li>
     *     <li>{@code persistentStreamProperties}: Properties of the persistent stream.</li>
     *     <li>{@code scheduler}: The {@link ScheduledExecutorService} for scheduling tasks.</li>
     *     <li>{@code batchSize}: The number of events to fetch in a single batch.</li>
     *     <li>{@code context}: The context in which the persistent stream operates.</li>
     * </ul>
     *
     * @return A {@link PersistentStreamMessageSourceFactory} that constructs {@link PersistentStreamMessageSource}
     * instances.
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory() {
        return new DefaultPersistentStreamMessageSourceFactory();
    }

    /**
     * Creates a {@code ConfigurerModule} to configure
     * {@link SequencingPolicy sequencing policies} for persistent streams
     * connected to {@link SubscribingEventProcessor subscribing event processing} with a dead letter queue.
     *
     * @param processorProperties     Contains the configured event processing.
     * @param axonServerConfiguration Contains the persistent stream definitions.
     * @return A {@code ConfigurerModule} to configure
     * {@link SequencingPolicy sequencing policies} for persistent streams
     * connected to {@link SubscribingEventProcessor subscribing event processing} with a dead letter queue.
     */
    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public ConfigurationEnhancer persistentStreamProcessorsConfigurerModule(
            EventProcessorProperties processorProperties,
            AxonServerConfiguration axonServerConfiguration
    ) {
        return componentRegistry -> {
        };
//        return configurer -> configurer.eventProcessing(
//                processingConfigurer -> processorProperties.getProcessors()
//                                                           .entrySet()
//                                                           .stream()
//                                                           .filter(e -> e.getValue().getMode()
//                                                                         .equals(EventProcessorProperties.Mode.SUBSCRIBING))
//                                                           .filter(e -> e.getValue().getDlq().isEnabled())
//                                                           .filter(e -> axonServerConfiguration.getPersistentStreams()
//                                                                                               .containsKey(
//                                                                                                       e.getValue()
//                                                                                                        .source()))
//                                                           .forEach(e -> {
//                                                               AxonServerConfiguration.PersistentStreamSettings persistentStreamConfig =
//                                                                       axonServerConfiguration.getPersistentStreams()
//                                                                                              .get(e.getValue()
//                                                                                                    .source());
//                                                               processingConfigurer.registerSequencingPolicy(
//                                                                       e.getKey(),
//                                                                       // TODO #3520
//                                                                       null
////                                                                       new PersistentStreamSequencingPolicyProvider(
////                                                                               e.getKey(),
////                                                                               persistentStreamConfig.getSequencingPolicy(),
////                                                                               persistentStreamConfig.getSequencingPolicyParameters()
////                                                                       )
//                                                               );
//                                                           })
//        );
    }
}
