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

package org.axonframework.spring.jdbc;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringDataSourceConnectionProviderTest.Context.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SpringDataSourceConnectionProviderTest {

    private Connection mockConnection;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ConnectionProvider connectionProvider;
    private SpringTransactionManager springTransactionManager;

    @BeforeEach
    void setUp() {
        springTransactionManager = new SpringTransactionManager(transactionManager);
    }

    @DirtiesContext
    @Transactional
    @Test
    void connectionNotCommittedWhenTransactionScopeOutsideUnitOfWork() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            fail("Should be using an already existing connection.");
            return null;
        });

        LegacyUnitOfWork<?> uow = LegacyDefaultUnitOfWork.startAndGet(null);
        Connection connection = connectionProvider.getConnection();

        connection.commit();

        uow.commit();
    }

    @Test
    void connectionCommittedWhenTransactionScopeInsideUnitOfWork() throws Exception {
        doAnswer(invocation -> {
            final Object spy = spy(invocation.callRealMethod());
            mockConnection = (Connection) spy;
            return spy;
        }).when(dataSource).getConnection();

        LegacyUnitOfWork<?> uow = LegacyDefaultUnitOfWork.startAndGet(null);
        Transaction transaction = springTransactionManager.startTransaction();
        uow.onCommit(u -> transaction.commit());
        uow.onRollback(u -> transaction.rollback());

        Connection innerConnection = connectionProvider.getConnection();
        assertNotSame(innerConnection, mockConnection);

        innerConnection.commit();
        verify(mockConnection, never()).commit();

        uow.commit();
        verify(mockConnection).commit();
    }

    @Configuration
    static class Context {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:axontest");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
            return spy(dataSource);
        }

        @Bean
        public PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        public ConnectionProvider connectionProvider(DataSource dataSource) {
            return new UnitOfWorkAwareConnectionProviderWrapper(new SpringDataSourceConnectionProvider(dataSource));
        }
    }
}
