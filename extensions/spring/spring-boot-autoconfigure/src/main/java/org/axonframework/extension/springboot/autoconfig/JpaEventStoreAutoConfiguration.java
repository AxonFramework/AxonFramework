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

package org.axonframework.extension.springboot.autoconfig;

import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateBasedJpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateBasedJpaEventStorageEngineConfiguration;
import org.axonframework.eventsourcing.eventstore.jpa.JpaPollingEventCoordinator;
import org.axonframework.extension.springboot.JpaEventStorageEngineConfigurationProperties;
import org.axonframework.extension.springboot.util.RegisterDefaultEntities;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.function.UnaryOperator;

/**
 * Autoconfiguration class for Axon's JPA specific event store engine.
 *
 * @author Sara Pelligrini
 * @author Simon Zambrovski
 * @since 4.0
 */
@AutoConfiguration(after = {
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.axonframework.extension.springboot.autoconfig.AxonServerAutoConfiguration.class,
        JpaAutoConfiguration.class})
@ConditionalOnBean({EntityManagerFactory.class, PlatformTransactionManager.class})
@ConditionalOnMissingBean(value = {EventStore.class, EventStorageEngine.class})
@RegisterDefaultEntities(packages = {
        "org.axonframework.eventsourcing.eventstore.jpa"
})
@EnableConfigurationProperties(JpaEventStorageEngineConfigurationProperties.class)
public class JpaEventStoreAutoConfiguration {

    /**
     * Defines the default configuration for the {@link AggregateBasedJpaEventStorageEngine}.
     * @param entityManagerProvider  The provider for the EntityManager to use
     * @param persistenceExceptionResolver The resolver to convert exceptions from the underlying persistence layer
     * @param properties properties defining the configuration of the event storage engine
     * @return a unary operator adding the defined properties to the configuration
     */
    @ConditionalOnMissingBean
    @Bean
    public UnaryOperator<AggregateBasedJpaEventStorageEngineConfiguration> jpaEventStorageEngineConfigurationCustomizer(
            EntityManagerProvider entityManagerProvider,
            PersistenceExceptionResolver persistenceExceptionResolver,
            JpaEventStorageEngineConfigurationProperties properties
    ) {
        return config ->
                config
                        .batchSize(properties.batchSize())
                        .gapCleaningThreshold(properties.gapCleaningThreshold())
                        .gapTimeout(properties.gapTimeout())
                        .lowestGlobalSequence(properties.lowestGlobalSequence())
                        .maxGapOffset(properties.maxGapOffset())
                        .persistenceExceptionResolver(persistenceExceptionResolver)
                        .eventCoordinator(
                                new JpaPollingEventCoordinator(
                                        entityManagerProvider,
                                        Duration.ofMillis(properties.pollingInterval())
                                )
                        );
    }

    /**
     * Defines an AggregateBasedJpaEventStorageEngine bean.
     *
     * @param entityManagerProvider The provider for the EntityManager to use
     * @param transactionManager    The transaction manager that manages the database transactions
     * @param eventConverter        The converter to convert events to persistence format
     * @param configurer            The operator setting required configuration
     * @return an AggregateBasedJpaEventStorageEngine bean
     */
    @ConditionalOnMissingBean
    @Bean
    public EventStorageEngine jpaEventStorageEngine(EntityManagerProvider entityManagerProvider,
                                                    TransactionManager transactionManager,
                                                    EventConverter eventConverter,
                                                    UnaryOperator<AggregateBasedJpaEventStorageEngineConfiguration> configurer) {
        return new AggregateBasedJpaEventStorageEngine(
                entityManagerProvider,
                transactionManager,
                eventConverter,
                configurer
        );
    }
}
