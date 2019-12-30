/*
 * Copyright (c) 2010-2019. Axon Framework
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
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests JDBC auto-configuration.
 *
 * @author Milan Savic
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(classes = JdbcAutoConfigurationTest.Context.class)
@EnableAutoConfiguration(exclude = {
        JpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        AxonServerAutoConfiguration.class
})
public class JdbcAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testContextInitialization() {
        assertNotNull(applicationContext);

        assertTrue(applicationContext.getBean(EventStorageEngine.class) instanceof JdbcEventStorageEngine);
        assertTrue(applicationContext.getBean(PersistenceExceptionResolver.class) instanceof JdbcSQLErrorCodesResolver);
        assertTrue(
                applicationContext.getBean(ConnectionProvider.class) instanceof UnitOfWorkAwareConnectionProviderWrapper
        );
        assertTrue(applicationContext.getBean(TokenStore.class) instanceof JdbcTokenStore);
        assertTrue(applicationContext.getBean(SagaStore.class) instanceof JdbcSagaStore);

        assertEquals(
                applicationContext.getBean(TokenStore.class),
                applicationContext.getBean(EventProcessingConfiguration.class).tokenStore("test")
        );
    }

    @Configuration
    public static class Context {

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
}
