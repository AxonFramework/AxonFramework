/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@ContextConfiguration(classes = SpringDataSourceConnectionProviderTest.Context.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringDataSourceConnectionProviderTest {

    private Connection mockConnection;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ConnectionProvider connectionProvider;
    private DefaultUnitOfWorkFactory unitOfWorkFactory;

    @Before
    public void setUp() throws Exception {
        unitOfWorkFactory = new DefaultUnitOfWorkFactory(new SpringTransactionManager(transactionManager));
    }

    @Transactional
    @Test
    public void testConnectionNotCommittedWhenTransactionScopeOutsideUnitOfWork() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            fail("Should be using an already existing connection.");
            return null;
        });

        UnitOfWork<?> uow = unitOfWorkFactory.createUnitOfWork(null);
        Connection connection = connectionProvider.getConnection();

        connection.commit();

        uow.commit();
    }

    @Test
    public void testConnectionCommittedWhenTransactionScopeInsideUnitOfWork() throws Exception {
        doAnswer(invocation -> {
            final Object spy = spy(invocation.callRealMethod());
            mockConnection = (Connection) spy;
            return spy;
        }).when(dataSource).getConnection();
        UnitOfWork<?> uow = unitOfWorkFactory.createUnitOfWork(null);
        Connection connection = connectionProvider.getConnection();
        assertNotSame(connection, mockConnection);

        connection.commit();
        verify(mockConnection, never()).commit();

        uow.commit();
        verify(mockConnection).commit();
    }

    @ImportResource("classpath:/META-INF/spring/db-context.xml")
    @Configuration
    public static class Context {

        @Bean
        public ConnectionProvider connectionProvider(DataSource dataSource) throws SQLException {
            return new UnitOfWorkAwareConnectionProviderWrapper(new SpringDataSourceConnectionProvider(dataSource));
        }
    }
}