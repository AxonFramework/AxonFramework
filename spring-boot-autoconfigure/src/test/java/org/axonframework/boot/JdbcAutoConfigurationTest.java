package org.axonframework.boot;

import org.axonframework.boot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.junit.*;
import org.junit.runner.*;
import org.ops4j.pax.exam.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests JDBC auto-configuration.
 *
 * @author Milan Savic
 */
@ContextConfiguration(classes = JdbcAutoConfigurationTest.Context.class)
@EnableAutoConfiguration(exclude = {
        JpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        AxonServerAutoConfiguration.class})
@RunWith(SpringRunner.class)
public class JdbcAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testContextInitialization() {
        assertNotNull(applicationContext);

        assertTrue(applicationContext.getBean(EventStorageEngine.class) instanceof JdbcEventStorageEngine);
        assertTrue(applicationContext.getBean(TokenStore.class) instanceof JdbcTokenStore);
        assertTrue(applicationContext.getBean(SagaStore.class) instanceof JdbcSagaStore);
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
