/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.boot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.boot.DistributedCommandBusProperties;
import org.axonframework.boot.EventProcessorProperties;
import org.axonframework.boot.SerializerProperties;
import org.axonframework.boot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.JavaSerializer;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.function.Function;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@org.springframework.context.annotation.Configuration
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.axonframework.boot.autoconfig.JpaAutoConfiguration",
        "org.axonframework.boot.autoconfig.ObjectMapperAutoConfiguration"
})
@EnableConfigurationProperties(value = {
        EventProcessorProperties.class,
        DistributedCommandBusProperties.class,
        SerializerProperties.class
})
public class AxonAutoConfiguration implements BeanClassLoaderAware {

    private final EventProcessorProperties eventProcessorProperties;
    private final SerializerProperties serializerProperties;
    private final ApplicationContext applicationContext;

    private ClassLoader beanClassLoader;

    public AxonAutoConfiguration(EventProcessorProperties eventProcessorProperties,
                                 SerializerProperties serializerProperties,
                                 ApplicationContext applicationContext) {
        this.eventProcessorProperties = eventProcessorProperties;
        this.serializerProperties = serializerProperties;
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnMissingBean
    public RevisionResolver revisionResolver() {
        return new AnnotationRevisionResolver();
    }

    @Bean
    @Primary
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "!eventSerializer,messageSerializer")
    public Serializer serializer(RevisionResolver revisionResolver) {
        return buildSerializer(revisionResolver, serializerProperties.getGeneral());
    }

    @Bean
    @Qualifier("messageSerializer")
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "messageSerializer")
    public Serializer messageSerializer(Serializer genericSerializer, RevisionResolver revisionResolver) {
        if (SerializerProperties.SerializerType.DEFAULT.equals(serializerProperties.getMessages())
                || serializerProperties.getGeneral().equals(serializerProperties.getMessages())) {
            return genericSerializer;
        }
        return buildSerializer(revisionResolver, serializerProperties.getMessages());
    }

    @Bean
    @Qualifier("eventSerializer")
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "eventSerializer")
    public Serializer eventSerializer(@Qualifier("messageSerializer") Serializer messageSerializer,
                                      Serializer generalSerializer,
                                      RevisionResolver revisionResolver) {
        if (SerializerProperties.SerializerType.DEFAULT.equals(serializerProperties.getEvents())
                || serializerProperties.getEvents().equals(serializerProperties.getMessages())) {
            return messageSerializer;
        } else if (serializerProperties.getGeneral().equals(serializerProperties.getEvents())) {
            return generalSerializer;
        }
        return buildSerializer(revisionResolver, serializerProperties.getEvents());
    }

    private Serializer buildSerializer(RevisionResolver revisionResolver,
                                       SerializerProperties.SerializerType serializerType) {
        switch (serializerType) {
            case JACKSON:
                Map<String, ObjectMapper> objectMapperBeans = applicationContext.getBeansOfType(ObjectMapper.class);
                ObjectMapper objectMapper = objectMapperBeans.containsKey("defaultAxonObjectMapper")
                        ? objectMapperBeans.get("defaultAxonObjectMapper")
                        : objectMapperBeans.values().stream().findFirst()
                                           .orElseThrow(() -> new NoClassDefFoundError(
                                                   "com/fasterxml/jackson/databind/ObjectMapper"
                                           ));
                ChainingConverter converter = new ChainingConverter(beanClassLoader);
                return new JacksonSerializer(objectMapper, revisionResolver, converter);
            case JAVA:
                return new JavaSerializer(revisionResolver);
            case XSTREAM:
            case DEFAULT:
            default:
                XStreamSerializer xStreamSerializer = new XStreamSerializer(revisionResolver);
                xStreamSerializer.getXStream().setClassLoader(beanClassLoader);
                return xStreamSerializer;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationDataProvider messageOriginProvider() {
        return new MessageOriginProvider();
    }

    @Qualifier("eventStore")
    @Bean(name = "eventBus")
    @ConditionalOnMissingBean(EventBus.class)
    @ConditionalOnBean(EventStorageEngine.class)
    public EmbeddedEventStore eventStore(EventStorageEngine storageEngine, AxonConfiguration configuration) {
        return new EmbeddedEventStore(storageEngine, configuration.messageMonitor(EventStore.class, "eventStore"));
    }

    @ConditionalOnMissingBean
    @Bean
    public CommandGateway commandGateway(CommandBus commandBus) {
        return new DefaultCommandGateway(commandBus);
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class})
    public SimpleEventBus eventBus(AxonConfiguration configuration) {
        return new SimpleEventBus(Integer.MAX_VALUE, configuration.messageMonitor(EventStore.class, "eventStore"));
    }

    @SuppressWarnings("unchecked")
    @Autowired
    public void configureEventHandling(EventHandlingConfiguration eventHandlingConfiguration,
                                       EventProcessingConfiguration eventProcessingConfiguration,
                                       ApplicationContext applicationContext) {
        eventProcessorProperties.getProcessors().forEach((k, v) -> {

            Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy =
                    resolveSequencingPolicy(applicationContext, v);
            eventHandlingConfiguration.configureSequencingPolicy(k, sequencingPolicy);

            if (v.getMode() == EventProcessorProperties.Mode.TRACKING) {
                TrackingEventProcessorConfiguration config = TrackingEventProcessorConfiguration
                        .forParallelProcessing(v.getThreadCount())
                        .andBatchSize(v.getBatchSize())
                        .andInitialSegmentsCount(v.getInitialSegmentCount());
                Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource =
                        resolveMessageSource(applicationContext, v);
                eventProcessingConfiguration.registerTrackingEventProcessor(k, messageSource, c -> config);
            } else {
                if (v.getSource() == null) {
                    eventProcessingConfiguration.registerSubscribingEventProcessor(k);
                } else {
                    eventProcessingConfiguration.registerSubscribingEventProcessor(
                            k, c -> applicationContext.getBean(v.getSource(), SubscribableMessageSource.class)
                    );
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> resolveMessageSource(
            ApplicationContext applicationContext, EventProcessorProperties.ProcessorSettings v) {
        Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource;
        if (v.getSource() == null) {
            messageSource = Configuration::eventStore;
        } else {
            messageSource = c -> applicationContext.getBean(v.getSource(), StreamableMessageSource.class);
        }
        return messageSource;
    }

    @SuppressWarnings("unchecked")
    private Function<Configuration, SequencingPolicy<? super EventMessage<?>>> resolveSequencingPolicy(
            ApplicationContext applicationContext, EventProcessorProperties.ProcessorSettings v) {
        Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy;
        if (v.getSequencingPolicy() != null) {
            sequencingPolicy = c -> applicationContext.getBean(v.getSequencingPolicy(), SequencingPolicy.class);
        } else {
            sequencingPolicy = c -> SequentialPerAggregatePolicy.instance();
        }
        return sequencingPolicy;
    }

    @ConditionalOnMissingBean(ignored = {DistributedCommandBus.class}, value = CommandBus.class)
    @Qualifier("localSegment")
    @Bean
    public SimpleCommandBus commandBus(TransactionManager txManager, AxonConfiguration axonConfiguration) {
        SimpleCommandBus commandBus =
                new SimpleCommandBus(txManager, axonConfiguration.messageMonitor(CommandBus.class, "commandBus"));

        commandBus.registerHandlerInterceptor(
                new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
        );
        return commandBus;
    }

    @ConditionalOnMissingBean(value = {QueryBus.class, QueryInvocationErrorHandler.class})
    @Qualifier("localSegment")
    @Bean
    public SimpleQueryBus queryBus(AxonConfiguration axonConfiguration, TransactionManager transactionManager) {
        return new SimpleQueryBus(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"),
                                  transactionManager,
                                  axonConfiguration.getComponent(QueryInvocationErrorHandler.class));
    }

    @ConditionalOnBean(QueryInvocationErrorHandler.class)
    @ConditionalOnMissingBean(QueryBus.class)
    @Qualifier("localSegment")
    @Bean
    public SimpleQueryBus queryBus(AxonConfiguration axonConfiguration,
                                   TransactionManager transactionManager,
                                   QueryInvocationErrorHandler eh) {
        return new SimpleQueryBus(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"),
                                  transactionManager,
                                  eh);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadlineManager deadlineManager(AxonConfiguration axonConfiguration,
                                           TransactionManager transactionManager) {
        return new SimpleDeadlineManager(
                new ConfigurationScopeAwareProvider(axonConfiguration),
                transactionManager);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }
}
