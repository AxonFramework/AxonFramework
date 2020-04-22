/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.springboot;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests JDBC auto-configuration.
 *
 * @author Milan Savic
 */
public class JdbcAutoConfigurationTest {

    @Test
    void testAllJdbcComponentsAutoConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcEventStorageEngine.class);
                    assertThat(context).getBean(EventStorageEngine.class).isInstanceOf(JdbcEventStorageEngine.class);
                    assertThat(context).getBean(EventStore.class).isInstanceOf(EmbeddedEventStore.class);
                    assertThat(context).getBean(TokenStore.class).isInstanceOf(JdbcTokenStore.class);
                    assertThat(context).getBean(SagaStore.class).isInstanceOf(JdbcSagaStore.class);
                    assertThat(context).getBean(TokenStore.class).isEqualTo(context.getBean(EventProcessingConfiguration.class).tokenStore("test"));
                    assertThat(context).getBean(PersistenceExceptionResolver.class).isInstanceOf(JdbcSQLErrorCodesResolver.class);
                    assertThat(context).getBean(ConnectionProvider.class).isInstanceOf(UnitOfWorkAwareConnectionProviderWrapper.class);
                });
    }

    @Test
    void testConfigurationOfEventBusPreventsEventStoreDefinition() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class, ExplicitEventBusContext.class)
                .run(context -> assertThat(context).doesNotHaveBean(EventStorageEngine.class)
                                                   .doesNotHaveBean(EventStore.class));
    }

    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    @ContextConfiguration
    @EnableAutoConfiguration(exclude = {
            JpaRepositoriesAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            AxonServerAutoConfiguration.class
    })
    static class Context {

        @Bean
        public DataSource dataSource() throws SQLException {
            DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
            Connection connection = mock(Connection.class);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(connection);
            return dataSource;
        }
    }

    @Configuration
    static class ExplicitEventBusContext {

        @Bean
        public EventBus eventBus() {
            return SimpleEventBus.builder().build();
        }
    }
}
