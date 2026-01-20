/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.DeadLetterSchema;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStoreConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.TokenSchema;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.messaging.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.messaging.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.messaging.eventsourcing.eventstore.jdbc.LegacyJdbcEventStorageEngine;
import org.axonframework.extension.springboot.autoconfig.JpaAutoConfiguration;
import org.axonframework.conversion.Serializer;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.extension.spring.jdbc.SpringDataSourceConnectionProvider;
import org.axonframework.extension.springboot.EventProcessorProperties;
import org.axonframework.extension.springboot.TokenStoreProperties;
import org.axonframework.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
@AutoConfigureAfter({JpaAutoConfiguration.class})
@AutoConfigureBefore(LegacyAxonAutoConfiguration.class)
public class JdbcAutoConfiguration {

    private final TokenStoreProperties tokenStoreProperties;

    public JdbcAutoConfiguration(TokenStoreProperties tokenStoreProperties) {
        this.tokenStoreProperties = tokenStoreProperties;
    }

    @Bean
    @ConditionalOnMissingBean({LegacyEventStorageEngine.class, EventSchema.class, EventStore.class})
    public EventSchema eventSchema() {
        return new EventSchema();
    }

    @Bean
    @ConditionalOnMissingBean({LegacyEventStorageEngine.class, EventBus.class, EventStore.class})
    public LegacyJdbcEventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                           PersistenceExceptionResolver persistenceExceptionResolver,
                                                           @Qualifier("eventSerializer") Serializer eventSerializer,
                                                           Configuration configuration,
                                                           ConnectionProvider connectionProvider,
                                                           TransactionManager transactionManager,
                                                           EventSchema eventSchema) {
        return LegacyJdbcEventStorageEngine.builder()
                                           .snapshotSerializer(defaultSerializer)
//                                           .upcasterChain(configuration.upcasterChain())
                                           .persistenceExceptionResolver(persistenceExceptionResolver)
                                           .eventSerializer(eventSerializer)
//                                           .snapshotFilter(configuration.snapshotFilter())
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
    public TokenStore tokenStore(ConnectionProvider connectionProvider,
                                 TokenSchema tokenSchema, ObjectMapper defaultAxonObjectMapper) {
        var config = JdbcTokenStoreConfiguration.DEFAULT
                .schema(tokenSchema)
                .claimTimeout(tokenStoreProperties.getClaimTimeout());
        var converter = new JacksonConverter(defaultAxonObjectMapper);
        return new JdbcTokenStore(connectionProvider::getConnection, converter, config);
    }

//    @Bean
//    @ConditionalOnMissingBean({SagaStore.class, SagaSqlSchema.class})
//    public JdbcSagaStore sagaStoreNoSchema(ConnectionProvider connectionProvider) {
//        return JdbcSagaStore.builder()
//                            .connectionProvider(connectionProvider)
//                            .sqlSchema(new GenericSagaSqlSchema())
//                            .build();
//    }

//    @Bean
//    @ConditionalOnMissingBean(SagaStore.class)
//    @ConditionalOnBean(SagaSqlSchema.class)
//    public JdbcSagaStore sagaStoreWithSchema(ConnectionProvider connectionProvider,
//                                             SagaSqlSchema schema) {
//        return JdbcSagaStore.builder()
//                            .connectionProvider(connectionProvider)
//                            .sqlSchema(schema)
//                            .build();
//    }

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterSchema deadLetterSchema() {
        return DeadLetterSchema.defaultSchema();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterQueueProviderConfigurerModule deadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            ConnectionProvider connectionProvider,
            TransactionManager transactionManager,
            DeadLetterSchema schema,
            @Qualifier("eventSerializer") Serializer eventSerializer,
            Serializer genericSerializer
    ) {
        // TODO #3517
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties
//                ,
//                processingGroup -> config -> JdbcSequencedDeadLetterQueue.builder()
//                                                                         .processingGroup(processingGroup)
//                                                                         .connectionProvider(connectionProvider)
//                                                                         .transactionManager(transactionManager)
//                                                                         .schema(schema)
//                                                                         .genericSerializer(genericSerializer)
//                                                                         .eventSerializer(eventSerializer)
//                                                                         .build()
        );
    }
}