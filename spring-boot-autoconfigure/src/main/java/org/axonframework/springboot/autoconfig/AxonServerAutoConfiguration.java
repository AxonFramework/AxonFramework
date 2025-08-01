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


import io.axoniq.axonserver.connector.control.ControlChannel;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ManagedChannelCustomizer;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.TopologyChangeListener;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventScheduler;
import org.axonframework.axonserver.connector.event.axon.DefaultPersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceDefinition;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamScheduledExecutorBuilder;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.PriorityResolver;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.EventProcessorProperties;
import org.axonframework.springboot.TagsConfigurationProperties;
import org.axonframework.springboot.service.connection.AxonServerConnectionDetails;
import org.axonframework.springboot.service.connection.PropertiesAxonServerConnectionDetails;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.springboot.util.ConditionalOnQualifiedBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configures Axon Server as implementation for the CommandBus, QueryBus and EventStore.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@AutoConfiguration
@AutoConfigureBefore(LegacyAxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
@EnableConfigurationProperties(TagsConfigurationProperties.class)
@ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
public class AxonServerAutoConfiguration implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Bean
    public AxonServerConfiguration axonServerConfiguration() {
        AxonServerConfiguration configuration = new AxonServerConfiguration();
        configuration.setComponentName(clientName(applicationContext.getId()));
        return configuration;
    }

    private static String clientName(@Nullable String id) {
        if (id == null) {
            return "Unnamed";
        } else if (id.contains(":")) {
            return id.substring(0, id.indexOf(":"));
        }
        return id;
    }

    @Configuration
    @ConditionalOnMissingClass(value = "org.springframework.boot.autoconfigure.service.connection.ConnectionDetails")
    public static class DefaultConnectionManagerConfiguration {

        @Bean
        public AxonServerConnectionManager platformConnectionManager(AxonServerConfiguration axonServerConfig,
                                                                     TagsConfigurationProperties tagProperties,
                                                                     ManagedChannelCustomizer managedChannelCustomizer) {
            return AxonServerConnectionManager.builder()
                                              .routingServers(axonServerConfig.getServers())
                                              .axonServerConfiguration(axonServerConfig)
                                              .tagsConfiguration(tagProperties.toTagsConfiguration())
                                              .channelCustomizer(managedChannelCustomizer)
                                              .build();
        }
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.autoconfigure.service.connection.ConnectionDetails")
    public static class ConnectionDetailsConnectionManagerConfiguration {

        @Bean
        @ConditionalOnMissingBean(AxonServerConnectionDetails.class)
        PropertiesAxonServerConnectionDetails axonServerConnectionDetails(AxonServerConfiguration configuration) {
            return new PropertiesAxonServerConnectionDetails(configuration);
        }

        @Bean
        public AxonServerConnectionManager platformConnectionManager(AxonServerConnectionDetails connectionDetails,
                                                                     AxonServerConfiguration axonServerConfig,
                                                                     TagsConfigurationProperties tagProperties,
                                                                     ManagedChannelCustomizer managedChannelCustomizer) {
            return AxonServerConnectionManager.builder()
                                              .routingServers(connectionDetails.routingServers())
                                              .axonServerConfiguration(axonServerConfig)
                                              .tagsConfiguration(tagProperties.toTagsConfiguration())
                                              .channelCustomizer(managedChannelCustomizer)
                                              .build();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ManagedChannelCustomizer managedChannelCustomizer() {
        return ManagedChannelCustomizer.identity();
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategy routingStrategy() {
        return new AnnotationRoutingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public PriorityResolver<CommandMessage<?>> commandPriorityCalculator() {
        return message -> 0;
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryPriorityCalculator queryPriorityCalculator() {
        return QueryPriorityCalculator.defaultQueryPriorityCalculator();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryInvocationErrorHandler queryInvocationErrorHandler() {
        return LoggingQueryInvocationErrorHandler.builder().build();
    }

    @ConditionalOnMissingBean
    @Bean
    public TargetContextResolver<Message<?>> targetContextResolver() {
        return TargetContextResolver.noOp();
    }

    @Bean
    @ConditionalOnMissingClass("org.axonframework.extensions.multitenancy.autoconfig.MultiTenancyAxonServerAutoConfiguration")
    public EventProcessorInfoConfiguration processorInfoConfiguration(
            EventProcessingConfiguration eventProcessingConfiguration,
            AxonServerConnectionManager connectionManager,
            AxonServerConfiguration configuration
    ) {
        // TODO #3521
        return new EventProcessorInfoConfiguration(/*c -> eventProcessingConfiguration*/null,
                                                                                        c -> connectionManager,
                                                                                        c -> configuration);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public EventScheduler eventScheduler(@Qualifier("eventSerializer") Serializer eventSerializer,
                                         AxonServerConnectionManager connectionManager) {
        return AxonServerEventScheduler.builder()
                                       .eventSerializer(eventSerializer)
                                       .connectionManager(connectionManager)
                                       .build();
    }

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
     * Creates a {@link ConfigurerModule} to invoke {@link EventProcessingConfigurer::usingSubscribingEventProcessors}
     *
     * @param executorBuilder         The {@link java.util.concurrent.ScheduledExecutorService} builder used during
     *                                construction of the {@link PersistentStreamMessageSourceDefinition}.
     * @param psFactory               used during construction of the {@link PersistentStreamMessageSourceDefinition}.
     * @param axonServerConfiguration Contains the persistent stream settings.
     * @return A {@link ConfigurerModule} to configure
     */
    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.auto-persistent-streams-enable")
    public ConfigurerModule autoPersistentStreamMessageSourceDefinitionBuilder(
            PersistentStreamScheduledExecutorBuilder executorBuilder,
            PersistentStreamMessageSourceFactory psFactory,
            AxonServerConfiguration axonServerConfiguration) {
        AxonServerConfiguration.PersistentStreamSettings psSettings = axonServerConfiguration.getAutoPersistentStreamsSettings();
        return configurer -> configurer.eventProcessing().usingSubscribingEventProcessors(
                processingGroupName -> {
                    String psName = processingGroupName + "-stream";
                    return new PersistentStreamMessageSourceDefinition(
                            processingGroupName,
                            new PersistentStreamProperties(psName,
                                                           psSettings.getInitialSegmentCount(),
                                                           psSettings.getSequencingPolicy(),
                                                           psSettings.getSequencingPolicyParameters(),
                                                           psSettings.getInitialPosition(),
                                                           psSettings.getFilter()),
                            executorBuilder.build(psSettings.getThreadCount(), psName),
                            psSettings.getBatchSize(),
                            null,
                            psFactory
                    );
                });
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
     * Creates a {@link ConfigurerModule} to configure
     * {@link org.axonframework.eventhandling.async.SequencingPolicy sequencing policies} for persistent streams
     * connected to {@link org.axonframework.eventhandling.SubscribingEventProcessor subscribing event processors} with
     * a dead letter queue.
     *
     * @param processorProperties     Contains the configured event processors.
     * @param axonServerConfiguration Contains the persistent stream definitions.
     * @return A {@link ConfigurerModule} to configure
     * {@link org.axonframework.eventhandling.async.SequencingPolicy sequencing policies} for persistent streams
     * connected to {@link org.axonframework.eventhandling.SubscribingEventProcessor subscribing event processors} with
     * a dead letter queue.
     */
    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public ConfigurerModule persistentStreamProcessorsConfigurerModule(
            EventProcessorProperties processorProperties,
            AxonServerConfiguration axonServerConfiguration
    ) {
        return configurer -> configurer.eventProcessing(
                processingConfigurer -> processorProperties.getProcessors()
                                                           .entrySet()
                                                           .stream()
                                                           .filter(e -> e.getValue().getMode()
                                                                         .equals(EventProcessorProperties.Mode.SUBSCRIBING))
                                                           .filter(e -> e.getValue().getDlq().isEnabled())
                                                           .filter(e -> axonServerConfiguration.getPersistentStreams()
                                                                                               .containsKey(
                                                                                                       e.getValue()
                                                                                                        .getSource()))
                                                           .forEach(e -> {
                                                               AxonServerConfiguration.PersistentStreamSettings persistentStreamConfig =
                                                                       axonServerConfiguration.getPersistentStreams()
                                                                                              .get(e.getValue()
                                                                                                    .getSource());
                                                               processingConfigurer.registerSequencingPolicy(
                                                                       e.getKey(),
                                                                       // TODO #3520
                                                                       null
//                                                                       new PersistentStreamSequencingPolicyProvider(
//                                                                               e.getKey(),
//                                                                               persistentStreamConfig.getSequencingPolicy(),
//                                                                               persistentStreamConfig.getSequencingPolicyParameters()
//                                                                       )
                                                               );
                                                           })
        );
    }

    @Bean
    @ConditionalOnBean
    public ConfigurerModule topologyChangeListenerConfigurerModule(
            AxonServerConnectionManager platformConnectionManager,
            List<TopologyChangeListener> changeListeners
    ) {
        // ConditionalOnBean does not work for collections of beans, as it simply creates an empty collection.
        if (changeListeners.isEmpty()) {
            return configurer -> { /*Noop*/ };
        }
        return configurer -> configurer.onInitialize(config -> config.onStart(Phase.INSTRUCTION_COMPONENTS, () -> {
            ControlChannel defaultControlChannel = platformConnectionManager.getConnection().controlChannel();
            changeListeners.forEach(defaultControlChannel::registerTopologyChangeHandler);
        }));
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
