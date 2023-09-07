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

package org.axonframework.springboot.autoconfig;

import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.DefaultCommandBusSpanFactory;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.DefaultEventBusSpanFactory;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventsourcing.DefaultSnapshotterSpanFactory;
import org.axonframework.eventsourcing.SnapshotterSpanFactory;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.queryhandling.DefaultQueryBusSpanFactory;
import org.axonframework.queryhandling.DefaultQueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryUpdateEmitterSpanFactory;
import org.axonframework.springboot.TracingProperties;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TracingHandlerEnhancerDefinition;
import org.axonframework.tracing.attributes.AggregateIdentifierSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageIdSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageNameSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageTypeSpanAttributesProvider;
import org.axonframework.tracing.attributes.MetadataSpanAttributesProvider;
import org.axonframework.tracing.attributes.PayloadTypeSpanAttributesProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Configures common tracing components for Axon Framework. Defaults to the {@link NoOpSpanFactory} if no other
 * {@link SpanFactory} bean is configured.
 * <p>
 * You can define additional {@link SpanAttributesProvider}s by defining your own implementations as a bean or a
 * {@link org.springframework.stereotype.Component}. These will be picked up automatically.
 *
 * @author Mitchell Herrijgers
 * @see OpenTelemetryAutoConfiguration
 * @since 4.6.0
 */
@AutoConfiguration
@AutoConfigureBefore({AxonServerAutoConfiguration.class, AxonAutoConfiguration.class})
@EnableConfigurationProperties(TracingProperties.class)
public class AxonTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpanFactory.class)
    public SpanFactory spanFactory() {
        return NoOpSpanFactory.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(SnapshotterSpanFactory.class)
    public SnapshotterSpanFactory snapshotterSpanFactory(SpanFactory spanFactory, TracingProperties properties) {
        return DefaultSnapshotterSpanFactory.builder()
                                            .spanFactory(spanFactory)
                                            .aggregateTypeInSpanName(properties.getSnapshotter()
                                                                               .isAggregateTypeInSpanName())
                                            .separateTrace(properties.getSnapshotter().isSeparateTrace())
                                            .build();
    }

    @Bean
    @ConditionalOnMissingBean(CommandBusSpanFactory.class)
    public CommandBusSpanFactory commandBusSpanFactory(SpanFactory spanFactory, TracingProperties properties) {
        TracingProperties.CommandBusProperties commandBus = properties.getCommandBus();
        return DefaultCommandBusSpanFactory.builder()
                                           .spanFactory(spanFactory)
                                           .distributedInSameTrace(commandBus.isDistributedInSameTrace())
                                           .build();
    }

    @Bean
    @ConditionalOnMissingBean(QueryBusSpanFactory.class)
    public QueryBusSpanFactory queryBusSpanFactory(SpanFactory spanFactory, TracingProperties properties) {
        TracingProperties.QueryBusProperties commandBus = properties.getQueryBus();
        return DefaultQueryBusSpanFactory.builder()
                                         .spanFactory(spanFactory)
                                         .distributedInSameTrace(commandBus.isDistributedInSameTrace())
                                         .build();
    }

    @Bean
    @ConditionalOnMissingBean(QueryUpdateEmitterSpanFactory.class)
    public QueryUpdateEmitterSpanFactory queryUpdateEmitterSpanFactory(SpanFactory spanFactory) {
        return DefaultQueryUpdateEmitterSpanFactory.builder()
                                                   .spanFactory(spanFactory)
                                                   .build();
    }

    @Bean
    @ConditionalOnMissingBean(EventBusSpanFactory.class)
    public EventBusSpanFactory eventBusSpanFactory(SpanFactory spanFactory) {
        return DefaultEventBusSpanFactory.builder()
                                         .spanFactory(spanFactory)
                                         .build();
    }

    @Bean
    public HandlerEnhancerDefinition tracingHandlerEnhancerDefinition(SpanFactory spanFactory,
                                                                      TracingProperties properties) {
        return TracingHandlerEnhancerDefinition.builder()
                                               .spanFactory(spanFactory)
                                               .showEventSourcingHandlers(properties.isShowEventSourcingHandlers())
                                               .build();
    }

    @Bean
    public ConfigurerModule configurerModuleForTracing(List<SpanAttributesProvider> spanAttributesProviders) {
        return configurer -> configurer.onInitialize(config -> {
            SpanFactory spanFactory = config.spanFactory();
            spanAttributesProviders.forEach(spanFactory::registerSpanAttributeProvider);
        });
    }

    @Bean
    @ConditionalOnProperty(value = "axon.tracing.attribute-providers.aggregate-identifier", havingValue = "true", matchIfMissing = true)
    public SpanAttributesProvider aggregateIdentifierSpanAttributesProvider() {
        return new AggregateIdentifierSpanAttributesProvider();
    }

    @Bean
    @ConditionalOnProperty(value = "axon.tracing.attribute-providers.message-id", havingValue = "true", matchIfMissing = true)
    public SpanAttributesProvider messageIdSpanAttributesProvider() {
        return new MessageIdSpanAttributesProvider();
    }

    @Bean
    @ConditionalOnProperty(value = "axon.tracing.attribute-providers.message-name", havingValue = "true", matchIfMissing = true)
    public SpanAttributesProvider messageNameSpanAttributesProvider() {
        return new MessageNameSpanAttributesProvider();
    }

    @Bean
    @ConditionalOnProperty(value = "axon.tracing.attribute-providers.message-type", havingValue = "true", matchIfMissing = true)
    public SpanAttributesProvider messageTypeSpanAttributesProvider() {
        return new MessageTypeSpanAttributesProvider();
    }

    @Bean
    @ConditionalOnProperty(value = "axon.tracing.attribute-providers.metadata", havingValue = "true", matchIfMissing = true)
    public SpanAttributesProvider metadataSpanAttributesProvider() {
        return new MetadataSpanAttributesProvider();
    }

    @Bean
    @ConditionalOnProperty(value = "axon.tracing.attribute-providers.payload-type", havingValue = "true", matchIfMissing = true)
    public SpanAttributesProvider payloadTypeSpanAttributesProvider() {
        return new PayloadTypeSpanAttributesProvider();
    }
}
