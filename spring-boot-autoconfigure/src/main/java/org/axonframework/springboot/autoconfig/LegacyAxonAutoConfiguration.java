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

import org.axonframework.axonserver.connector.TagsConfiguration;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.LegacyConfiguration;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotterSpanFactory;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.spring.eventsourcing.SpringAggregateSnapshotter;
import org.axonframework.springboot.DistributedCommandBusProperties;
import org.axonframework.springboot.TagsConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@AutoConfiguration
@AutoConfigureAfter(EventProcessingAutoConfiguration.class)
@EnableConfigurationProperties(value = {
        DistributedCommandBusProperties.class,
        TagsConfigurationProperties.class
})
public class LegacyAxonAutoConfiguration {

    private final TagsConfigurationProperties tagsConfigurationProperties;

    public LegacyAxonAutoConfiguration(TagsConfigurationProperties tagsConfigurationProperties) {
        this.tagsConfigurationProperties = tagsConfigurationProperties;
    }

    @Bean
    public TagsConfiguration tagsConfiguration() {
        return tagsConfigurationProperties.toTagsConfiguration();
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
}
