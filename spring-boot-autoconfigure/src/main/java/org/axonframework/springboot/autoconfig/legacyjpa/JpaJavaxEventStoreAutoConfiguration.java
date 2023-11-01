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

package org.axonframework.springboot.autoconfig.legacyjpa;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.legacyjpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.JdbcAutoConfiguration;
import org.axonframework.springboot.autoconfig.JpaEventStoreAutoConfiguration;
import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.persistence.EntityManagerFactory;

/**
 * Autoconfiguration class for Axon's JPA specific event store components.
 *
 * @author Sara Pelligrini
 * @since 4.0
 * @deprecated in favor of using {@link JpaEventStoreAutoConfiguration} which moved to jakarta.
 */
@Deprecated
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class})
@ConditionalOnExpression("${axon.axonserver.enabled:true} == false")
@AutoConfigureBefore({JpaEventStoreAutoConfiguration.class, JdbcAutoConfiguration.class})
@AutoConfigureAfter({AxonServerAutoConfiguration.class, JpaJavaxAutoConfiguration.class})
@RegisterDefaultEntities(packages = {
        "org.axonframework.eventsourcing.eventstore.jpa"
})
public class JpaJavaxEventStoreAutoConfiguration {

    @Bean
    public EventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 org.axonframework.config.Configuration configuration,
                                                 EntityManagerProvider entityManagerProvider,
                                                 TransactionManager transactionManager) {
        return JpaEventStorageEngine.builder()
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
