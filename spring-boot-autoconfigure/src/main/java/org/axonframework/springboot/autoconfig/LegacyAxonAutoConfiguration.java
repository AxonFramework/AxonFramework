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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.thoughtworks.xstream.XStream;
import jakarta.annotation.Nonnull;
import org.apache.avro.message.SchemaStore;
import org.axonframework.axonserver.connector.TagsConfiguration;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.LegacyConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotterSpanFactory;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingContentTypeConverter;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.avro.AvroSerializer;
import org.axonframework.serialization.avro.AvroSerializerStrategy;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.eventsourcing.SpringAggregateSnapshotter;
import org.axonframework.springboot.DistributedCommandBusProperties;
import org.axonframework.springboot.EventProcessorProperties;
import org.axonframework.springboot.SerializerProperties;
import org.axonframework.springboot.TagsConfigurationProperties;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import static java.lang.String.format;
import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@AutoConfiguration
@AutoConfigureAfter(EventProcessingAutoConfiguration.class)
@EnableConfigurationProperties(value = {
        EventProcessorProperties.class,
        DistributedCommandBusProperties.class,
        SerializerProperties.class,
        TagsConfigurationProperties.class
})
public class LegacyAxonAutoConfiguration implements BeanClassLoaderAware {

    private final EventProcessorProperties eventProcessorProperties;
    private final SerializerProperties serializerProperties;
    private final TagsConfigurationProperties tagsConfigurationProperties;
    private final ApplicationContext applicationContext;

    private ClassLoader beanClassLoader;

    public LegacyAxonAutoConfiguration(EventProcessorProperties eventProcessorProperties,
                                       SerializerProperties serializerProperties,
                                       TagsConfigurationProperties tagsConfigurationProperties,
                                       ApplicationContext applicationContext) {
        this.eventProcessorProperties = eventProcessorProperties;
        this.serializerProperties = serializerProperties;
        this.tagsConfigurationProperties = tagsConfigurationProperties;
        this.applicationContext = applicationContext;
    }

    @Bean
    public TagsConfiguration tagsConfiguration() {
        return tagsConfigurationProperties.toTagsConfiguration();
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
        if (SerializerProperties.SerializerType.AVRO.equals(serializerProperties.getGeneral())) {
            throw new AxonConfigurationException(format(
                    "Invalid serializer type [%s] configured as general serializer. "
                            + "The Avro Serializer can be used as message or event serializer only.",
                    serializerProperties.getGeneral().name()
            ));
        }
        return buildSerializer(revisionResolver, serializerProperties.getGeneral(), null);
    }

    @Bean
    @Qualifier("messageSerializer")
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "messageSerializer")
    public Serializer messageSerializer(Serializer generalSerializer, RevisionResolver revisionResolver) {
        if (SerializerProperties.SerializerType.DEFAULT.equals(serializerProperties.getMessages())
                || serializerProperties.getGeneral().equals(serializerProperties.getMessages())) {
            return generalSerializer;
        }
        return buildSerializer(revisionResolver, serializerProperties.getMessages(), generalSerializer);
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
        return buildSerializer(revisionResolver, serializerProperties.getEvents(), generalSerializer);
    }

    @Bean
    public ConfigurerModule serializerConfigurer(@Qualifier("eventSerializer") Serializer eventSerializer,
                                                 @Qualifier("messageSerializer") Serializer messageSerializer,
                                                 Serializer generalSerializer) {
        return configurer -> {
            configurer.configureEventSerializer(c -> eventSerializer);
            configurer.configureMessageSerializer(c -> messageSerializer);
            configurer.configureSerializer(c -> generalSerializer);
        };
    }

    private Serializer buildSerializer(RevisionResolver revisionResolver,
                                       SerializerProperties.SerializerType serializerType,
                                       Serializer generalSerializer) {
        switch (serializerType) {
            case AVRO:
                Map<String, SchemaStore> schemaStoreBeans = beansOfTypeIncludingAncestors(applicationContext,
                                                                                          SchemaStore.class);
                SchemaStore schemaStore = schemaStoreBeans.containsKey("defaultAxonSchemaStore")
                        ? schemaStoreBeans.get("defaultAxonSchemaStore")
                        : schemaStoreBeans.values().stream().findFirst()
                                          .orElseThrow(() -> new NoSuchBeanDefinitionException(SchemaStore.class));

                if (generalSerializer == null) {
                    throw new AxonConfigurationException(
                            "General serializer is mandatory as a fallback Avro Serializer, but none was provided."
                    );
                }
                Map<String, AvroSerializerStrategy> serializationStrategies = beansOfTypeIncludingAncestors(
                        applicationContext,
                        AvroSerializerStrategy.class);
                AvroSerializer.Builder builder = AvroSerializer.builder()
                                                               .schemaStore(schemaStore)
                                                               .serializerDelegate(generalSerializer)
                                                               .revisionResolver(revisionResolver);
                serializationStrategies.values().forEach(builder::addSerializerStrategy);
                return builder.build();

            case JACKSON:
                Map<String, ObjectMapper> objectMapperBeans = beansOfTypeIncludingAncestors(applicationContext,
                                                                                            ObjectMapper.class);
                ObjectMapper objectMapper = objectMapperBeans.containsKey("defaultAxonObjectMapper")
                        ? objectMapperBeans.get("defaultAxonObjectMapper")
                        : objectMapperBeans.values().stream().findFirst()
                                           .orElseThrow(() -> new NoSuchBeanDefinitionException(ObjectMapper.class));
                ChainingContentTypeConverter converter = new ChainingContentTypeConverter(beanClassLoader);
                return JacksonSerializer.builder()
                                        .revisionResolver(revisionResolver)
                                        .converter(converter)
                                        .objectMapper(objectMapper)
                                        .build();
            case CBOR:
                Map<String, CBORMapper> cborMapperBeans = beansOfTypeIncludingAncestors(applicationContext,
                                                                                        CBORMapper.class);
                ObjectMapper cborMapper = cborMapperBeans.containsKey("defaultAxonCborObjectMapper")
                        ? cborMapperBeans.get("defaultAxonCborObjectMapper")
                        : cborMapperBeans.values().stream().findFirst()
                                         .orElseThrow(() -> new NoSuchBeanDefinitionException(CBORMapper.class));
                ChainingContentTypeConverter cborConverter = new ChainingContentTypeConverter(beanClassLoader);
                return JacksonSerializer.builder()
                                        .revisionResolver(revisionResolver)
                                        .converter(cborConverter)
                                        .objectMapper(cborMapper)
                                        .build();
            case XSTREAM:
            case DEFAULT:
            default:
                Map<String, XStream> xStreamBeans = beansOfTypeIncludingAncestors(applicationContext, XStream.class);
                XStream xStream = xStreamBeans.containsKey("defaultAxonXStream")
                        ? xStreamBeans.get("defaultAxonXStream")
                        : xStreamBeans.values().stream().findFirst()
                                      .orElseThrow(() -> new NoSuchBeanDefinitionException(XStream.class));
                return XStreamSerializer.builder()
                                        .xStream(xStream)
                                        .revisionResolver(revisionResolver)
                                        .classLoader(beanClassLoader)
                                        .build();
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
    @ConditionalOnBean(LegacyEventStorageEngine.class)
    public LegacyEmbeddedEventStore eventStore(LegacyEventStorageEngine storageEngine,
                                               LegacyConfiguration configuration) {
        return LegacyEmbeddedEventStore.builder()
                                       .storageEngine(storageEngine)
                                       .messageMonitor(configuration.messageMonitor(
                                               LegacyEventStore.class, "eventStore"
                                       ))
                                       .spanFactory(configuration.getComponent(EventBusSpanFactory.class))
                                       .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public CommandGateway commandGateway(CommandBus commandBus) {
        return new DefaultCommandGateway(commandBus, new ClassBasedMessageTypeResolver());
    }

    @ConditionalOnMissingBean
    @Bean
    public QueryGateway queryGateway(QueryBus queryBus) {
        return DefaultQueryGateway.builder().queryBus(queryBus).build();
    }

    @Bean
    @ConditionalOnMissingBean({LegacyEventStorageEngine.class, EventBus.class})
    public SimpleEventBus eventBus(LegacyConfiguration configuration) {
        return SimpleEventBus.builder()
                             .messageMonitor(configuration.messageMonitor(LegacyEventStore.class, "eventStore"))
                             .spanFactory(configuration.getComponent(EventBusSpanFactory.class))
                             .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public EventSink eventSink(EventBus eventBus) {
        return (context, events) -> {
            eventBus.publish(events);
            return FutureUtils.emptyCompletedFuture();
        };
    }

    @ConditionalOnMissingBean
    @Bean
    public MessageTypeResolver messageTypeResolver() {
        return new ClassBasedMessageTypeResolver();
    }

    @ConditionalOnMissingBean
    @Bean
    public EventGateway eventGateway(EventSink eventSink, MessageTypeResolver messageTypeResolver) {
        return new DefaultEventGateway(eventSink, messageTypeResolver);
    }

    @ConditionalOnMissingBean(Snapshotter.class)
    @ConditionalOnBean(LegacyEventStore.class)
    @Bean
    public SpringAggregateSnapshotter aggregateSnapshotter(LegacyConfiguration configuration,
                                                           HandlerDefinition handlerDefinition,
                                                           ParameterResolverFactory parameterResolverFactory,
                                                           LegacyEventStore eventStore,
                                                           TransactionManager transactionManager,
                                                           SnapshotterSpanFactory spanFactory) {
        return SpringAggregateSnapshotter.builder()
                                         .repositoryProvider(configuration::repository)
                                         .transactionManager(transactionManager)
                                         .eventStore(eventStore)
                                         .parameterResolverFactory(parameterResolverFactory)
                                         .handlerDefinition(handlerDefinition)
                                         .spanFactory(spanFactory)
                                         .build();
    }

    @SuppressWarnings("unchecked")
    @Autowired
    public void configureEventHandling(EventProcessingConfigurer eventProcessingConfigurer,
                                       ApplicationContext applicationContext) {
        eventProcessorProperties.getProcessors().forEach((name, settings) -> {
            Function<LegacyConfiguration, SequencingPolicy> sequencingPolicy =
                    resolveSequencingPolicy(applicationContext, settings);
            eventProcessingConfigurer.registerSequencingPolicy(name, sequencingPolicy);

            if (settings.getMode() == EventProcessorProperties.Mode.POOLED) {
                eventProcessingConfigurer.registerPooledStreamingEventProcessor(
                        name,
                        resolveMessageSource(applicationContext, settings),
                        (config, builder) -> {
                            ScheduledExecutorService workerExecutor = Executors.newScheduledThreadPool(
                                    settings.getThreadCount(), new AxonThreadFactory("WorkPackage[" + name + "]")
                            );
                            config.onShutdown(workerExecutor::shutdown);
                            return builder.workerExecutor(workerExecutor)
                                          .initialSegmentCount(initialSegmentCount(settings, 16))
                                          .tokenClaimInterval(tokenClaimIntervalMillis(settings))
                                          .batchSize(settings.getBatchSize());
                        }
                );
            } else {
                if (settings.getSource() == null) {
                    eventProcessingConfigurer.registerSubscribingEventProcessor(name);
                } else {
                    eventProcessingConfigurer.registerSubscribingEventProcessor(
                            name,
                            c -> {
                                Object bean = applicationContext.getBean(settings.getSource());
                                // TODO #3520
//                                if (bean instanceof SubscribableMessageSourceDefinition) {
//                                    return ((SubscribableMessageSourceDefinition<? extends EventMessage<?>>) bean)
//                                            .create(c);
//                                }
                                if (bean instanceof SubscribableMessageSource) {
                                    return (SubscribableMessageSource<? extends EventMessage<?>>) bean;
                                }
                                throw new AxonConfigurationException(format(
                                        "Invalid message source [%s] configured for Event Processor [%s]. "
                                                + "The message source should be a SubscribableMessageSource or SubscribableMessageSourceFactory",
                                        settings.getSource(), name
                                ));
                            }
                    );
                }
            }
            if (settings.getDlq().getCache().isEnabled()) {
                eventProcessingConfigurer.registerDeadLetteringEventHandlerInvokerConfiguration(
                        name,
                        (c, builder) -> builder
                                .enableSequenceIdentifierCache()
                                .sequenceIdentifierCacheSize(settings.getDlq().getCache().getSize()));
            }
        });
    }

    private int initialSegmentCount(EventProcessorProperties.ProcessorSettings settings, int defaultCount) {
        return settings.getInitialSegmentCount() != null ? settings.getInitialSegmentCount() : defaultCount;
    }

    private long tokenClaimIntervalMillis(EventProcessorProperties.ProcessorSettings settings) {
        return settings.getTokenClaimIntervalTimeUnit().toMillis(settings.getTokenClaimInterval());
    }

    @SuppressWarnings("unchecked")
    private Function<LegacyConfiguration, StreamableMessageSource<TrackedEventMessage<?>>> resolveMessageSource(
            ApplicationContext applicationContext, EventProcessorProperties.ProcessorSettings v
    ) {
        Function<LegacyConfiguration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource;
        if (v.getSource() == null) {
            messageSource = LegacyConfiguration::eventStore;
        } else {
            messageSource = c -> applicationContext.getBean(v.getSource(), StreamableMessageSource.class);
        }
        return messageSource;
    }

    @SuppressWarnings("unchecked")
    private Function<LegacyConfiguration, SequencingPolicy> resolveSequencingPolicy(
            ApplicationContext applicationContext, EventProcessorProperties.ProcessorSettings v) {
        Function<LegacyConfiguration, SequencingPolicy> sequencingPolicy;
        if (v.getSequencingPolicy() != null) {
            sequencingPolicy = c -> applicationContext.getBean(v.getSequencingPolicy(), SequencingPolicy.class);
        } else {
            sequencingPolicy = c -> SequentialPerAggregatePolicy.instance();
        }
        return sequencingPolicy;
    }

    @ConditionalOnMissingBean(
            ignoredType = {
                    "org.axonframework.commandhandling.distributed.DistributedCommandBus",
                    "org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus"
            },
            value = CommandBus.class
    )
    @Qualifier("localSegment")
    @Bean
    public CommandBus commandBus(TransactionManager txManager, LegacyConfiguration axonConfiguration) {
        SimpleCommandBus commandBus = new SimpleCommandBus(txManager);
        return new InterceptingCommandBus(
                commandBus,
                List.of(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())),
                Collections.emptyList()
        );
    }

    @ConditionalOnMissingBean(value = QueryBus.class)
    @Qualifier("localSegment")
    @Bean
    public SimpleQueryBus queryBus(LegacyConfiguration axonConfiguration, TransactionManager transactionManager) {
        return SimpleQueryBus.builder()
                             .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                             .transactionManager(transactionManager)
                             .errorHandler(axonConfiguration.getComponent(
                                     QueryInvocationErrorHandler.class,
                                     () -> LoggingQueryInvocationErrorHandler.builder().build()
                             ))
                             .queryUpdateEmitter(axonConfiguration.getComponent(QueryUpdateEmitter.class))
                             .spanFactory(axonConfiguration.getComponent(QueryBusSpanFactory.class))
                             .build();
    }

    @Bean
    public QueryUpdateEmitter queryUpdateEmitter(LegacyConfiguration configuration) {
        return SimpleQueryUpdateEmitter.builder()
                                       .updateMessageMonitor(configuration.messageMonitor(
                                               QueryUpdateEmitter.class, "queryUpdateEmitter"
                                       ))
                                       .spanFactory(configuration.getComponent(QueryUpdateEmitterSpanFactory.class))
                                       .build();
    }

    @Override
    public void setBeanClassLoader(@Nonnull ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }
}
