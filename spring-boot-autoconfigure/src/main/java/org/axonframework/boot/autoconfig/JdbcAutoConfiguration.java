package org.axonframework.boot.autoconfig;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.jdbc.GenericSagaSqlSchema;
import org.axonframework.eventhandling.saga.repository.jdbc.JdbcSagaStore;
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

import javax.sql.DataSource;
import java.sql.SQLException;

@ConditionalOnBean(DataSource.class)
@Configuration
@AutoConfigureAfter(JpaAutoConfiguration.class)
public class JdbcAutoConfiguration {

    @ConditionalOnMissingBean({EventStorageEngine.class, EventStore.class})
    @Bean
    public EventStorageEngine eventStorageEngine(Serializer serializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 AxonConfiguration configuration,
                                                 ConnectionProvider connectionProvider,
                                                 TransactionManager transactionManager) {
        return new JdbcEventStorageEngine(serializer,
                                          configuration.upcasterChain(),
                                          persistenceExceptionResolver,
                                          eventSerializer,
                                          connectionProvider,
                                          transactionManager);
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
        return new JdbcTokenStore(connectionProvider, serializer);
    }

    @ConditionalOnMissingBean(SagaStore.class)
    @Bean
    public JdbcSagaStore sagaStore(ConnectionProvider connectionProvider, Serializer serializer) {
        return new JdbcSagaStore(connectionProvider, new GenericSagaSqlSchema(), serializer);
    }
}
