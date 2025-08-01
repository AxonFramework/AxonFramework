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

import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.LegacyConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.eventsourcing.eventstore.jpa.LegacyJpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;


/**
 * Autoconfiguration class for Axon's JPA specific event store components.
 *
 * @author Sara Pelligrini
 * @since 4.0
 */
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnMissingBean({LegacyEventStorageEngine.class, EventBus.class, LegacyEventStore.class})
@AutoConfigureAfter({AxonServerBusAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@AutoConfigureBefore(LegacyAxonAutoConfiguration.class)
@RegisterDefaultEntities(packages = {
        "org.axonframework.eventsourcing.eventstore.jpa"
})
public class JpaEventStoreAutoConfiguration {

    @Bean
    public LegacyEventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                       PersistenceExceptionResolver persistenceExceptionResolver,
                                                       @Qualifier("eventSerializer") Serializer eventSerializer,
                                                       LegacyConfiguration configuration,
                                                       EntityManagerProvider entityManagerProvider,
                                                       TransactionManager transactionManager) {
        return LegacyJpaEventStorageEngine.builder()
                                          .snapshotSerializer(defaultSerializer)
                                          .upcasterChain(configuration.upcasterChain())
                                          .persistenceExceptionResolver(persistenceExceptionResolver)
                                          .eventSerializer(eventSerializer)
                                          .snapshotFilter(configuration.snapshotFilter())
                                          .entityManagerProvider(entityManagerProvider)
                                          .transactionManager(transactionManager)
                                          .build();
    }
}
