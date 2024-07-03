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

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.deadletter.jdbc.DeadLetterSchema;
import org.axonframework.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.TokenSchema;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jdbc.GenericSagaSqlSchema;
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.modelling.saga.repository.jdbc.SagaSqlSchema;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.jdbc.SpringDataSourceConnectionProvider;
import org.axonframework.springboot.EventProcessorProperties;
import org.axonframework.springboot.TokenStoreProperties;
import org.axonframework.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Optional;
import javax.sql.DataSource;

/**
 * Autoconfiguration class for Axon's JDBC specific infrastructure components.
 *
 * @author Allard Buijze
 * @since 3.1
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(TokenStoreProperties.class)
@AutoConfigureAfter({JpaAutoConfiguration.class, JpaEventStoreAutoConfiguration.class})
@AutoConfigureBefore(AxonAutoConfiguration.class)
public class JdbcAutoConfiguration {

    private final TokenStoreProperties tokenStoreProperties;

    public JdbcAutoConfiguration(TokenStoreProperties tokenStoreProperties) {
        this.tokenStoreProperties = tokenStoreProperties;
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventSchema.class, EventStore.class})
    public EventSchema eventSchema() {
        return new EventSchema();
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class, EventStore.class})
    public EventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 org.axonframework.config.Configuration configuration,
                                                 ConnectionProvider connectionProvider,
                                                 TransactionManager transactionManager,
                                                 EventSchema eventSchema) {
        return JdbcEventStorageEngine.builder()
                                     .snapshotSerializer(defaultSerializer)
                                     .upcasterChain(configuration.upcasterChain())
                                     .persistenceExceptionResolver(persistenceExceptionResolver)
                                     .eventSerializer(eventSerializer)
                                     .snapshotFilter(configuration.snapshotFilter())
                                     .connectionProvider(connectionProvider)
                                     .transactionManager(transactionManager)
                                     .schema(eventSchema)
                                     .build();
    }

    @Bean
    @ConditionalOnMissingBean({PersistenceExceptionResolver.class, EventStore.class})
    public PersistenceExceptionResolver jdbcSQLErrorCodesResolver() {
        return new JdbcSQLErrorCodesResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider connectionProvider(DataSource dataSource) {
        return new UnitOfWorkAwareConnectionProviderWrapper(new SpringDataSourceConnectionProvider(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean({TokenStore.class, TokenSchema.class})
    public TokenSchema tokenSchema() {
        return new TokenSchema();
    }

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore tokenStore(ConnectionProvider connectionProvider, Serializer serializer,
                                 TokenSchema tokenSchema) {
        return JdbcTokenStore.builder()
                             .connectionProvider(connectionProvider)
                             .schema(tokenSchema)
                             .serializer(serializer)
                             .claimTimeout(tokenStoreProperties.getClaimTimeout())
                             .build();
    }

    @Bean
    @ConditionalOnMissingBean({SagaStore.class, SagaSqlSchema.class})
    public JdbcSagaStore sagaStoreNoSchema(ConnectionProvider connectionProvider, Serializer serializer) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .sqlSchema(new GenericSagaSqlSchema())
                            .serializer(serializer)
                            .build();
    }

    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    @ConditionalOnBean(SagaSqlSchema.class)
    public JdbcSagaStore sagaStoreWithSchema(ConnectionProvider connectionProvider, Serializer serializer,
                                             SagaSqlSchema schema) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .sqlSchema(schema)
                            .serializer(serializer)
                            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterSchema deadLetterSchema() {
        return DeadLetterSchema.defaultSchema();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterQueueProviderConfigurerModule deadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            Optional<AxonServerConfiguration> axonServerConfiguration,
            ConnectionProvider connectionProvider,
            TransactionManager transactionManager,
            DeadLetterSchema schema,
            @Qualifier("eventSerializer") Serializer eventSerializer,
            Serializer genericSerializer
    ) {
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties,
                axonServerConfiguration.orElse(null),
                processingGroup -> config -> JdbcSequencedDeadLetterQueue.builder()
                                                                         .processingGroup(processingGroup)
                                                                         .connectionProvider(connectionProvider)
                                                                         .transactionManager(transactionManager)
                                                                         .schema(schema)
                                                                         .genericSerializer(genericSerializer)
                                                                         .eventSerializer(eventSerializer)
                                                                         .build()
        );
    }
}
