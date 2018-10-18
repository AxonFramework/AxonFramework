/*
 * Copyright (c) 2010-2018. Axon Framework
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

/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jdbc.GenericSagaSqlSchema;
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.jdbc.SpringDataSourceConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import javax.sql.DataSource;

@ConditionalOnBean(DataSource.class)
@Configuration
@AutoConfigureAfter(value = {JpaAutoConfiguration.class, JpaEventStoreAutoConfiguration.class})
public class JdbcAutoConfiguration {

    @ConditionalOnMissingBean({EventStorageEngine.class, EventStore.class})
    @Bean
    public EventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 AxonConfiguration configuration,
                                                 ConnectionProvider connectionProvider,
                                                 TransactionManager transactionManager) {
        return JdbcEventStorageEngine.builder()
                                     .snapshotSerializer(defaultSerializer)
                                     .upcasterChain(configuration.upcasterChain())
                                     .persistenceExceptionResolver(persistenceExceptionResolver)
                                     .eventSerializer(eventSerializer)
                                     .connectionProvider(connectionProvider)
                                     .transactionManager(transactionManager)
                                     .build();
    }

    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    @Bean
    public PersistenceExceptionResolver dataSourcePersistenceExceptionResolver(DataSource dataSource)
            throws SQLException {
        return new SQLErrorCodesResolver(dataSource);
    }

    @ConditionalOnMissingBean({DataSource.class, PersistenceExceptionResolver.class, EventStore.class})
    @Bean
    public PersistenceExceptionResolver jdbcSQLErrorCodesResolver() {
        return new JdbcSQLErrorCodesResolver();
    }

    @ConditionalOnMissingBean
    @Bean
    public ConnectionProvider connectionProvider(DataSource dataSource) {
        return new UnitOfWorkAwareConnectionProviderWrapper(new SpringDataSourceConnectionProvider(dataSource));
    }

    @ConditionalOnMissingBean
    @Bean
    public TokenStore tokenStore(ConnectionProvider connectionProvider, Serializer serializer) {
        return JdbcTokenStore.builder()
                             .connectionProvider(connectionProvider)
                             .serializer(serializer)
                             .build();
    }

    @ConditionalOnMissingBean(SagaStore.class)
    @Bean
    public JdbcSagaStore sagaStore(ConnectionProvider connectionProvider, Serializer serializer) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .sqlSchema(new GenericSagaSqlSchema())
                            .serializer(serializer)
                            .build();
    }
}
