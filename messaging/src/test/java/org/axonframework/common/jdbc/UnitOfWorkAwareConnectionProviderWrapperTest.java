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

package org.axonframework.common.jdbc;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UnitOfWorkAwareConnectionProviderWrapperTest {

    private ConnectionProvider mockConnectionProvider;
    private Connection mockConnection;
    private UnitOfWorkAwareConnectionProviderWrapper testSubject;

    @Before
    public void setUp() throws Exception {
        mockConnectionProvider = mock(ConnectionProvider.class);
        mockConnection = mock(Connection.class);
        when(mockConnectionProvider.getConnection()).thenReturn(mockConnection);

        testSubject = new UnitOfWorkAwareConnectionProviderWrapper(mockConnectionProvider);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testConnectionReturnedImmediatelyWhenNoActiveUnitOfWork() throws SQLException {
        Connection actual = testSubject.getConnection();
        assertSame(actual, mockConnection);
    }

    @Test
    public void testConnectionIsWrappedWhenUnitOfWorkIsActive() throws SQLException {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        Connection actual = testSubject.getConnection();
        assertNotSame(actual, mockConnection);

        actual.close();

        verify(mockConnection, never()).close();

        uow.commit();

        verify(mockConnection).close();
    }

    @Test
    public void testWrappedConnectionBlocksCommitCallsUntilUnitOfWorkCommit() throws SQLException {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        when(mockConnection.getAutoCommit()).thenReturn(false);
        when(mockConnection.isClosed()).thenReturn(false);

        Connection actual = testSubject.getConnection();

        actual.commit();

        verify(mockConnection, never()).commit();
        verify(mockConnection, never()).close();

        uow.commit();

        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).commit();
        inOrder.verify(mockConnection).close();
    }

    @Test
    public void testWrappedConnectionRollsBackCallsWhenUnitOfWorkRollback() throws SQLException {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        when(mockConnection.getAutoCommit()).thenReturn(false);
        when(mockConnection.isClosed()).thenReturn(false);

        Connection actual = testSubject.getConnection();

        actual.close();
        actual.commit();

        verify(mockConnection, never()).commit();
        verify(mockConnection, never()).close();

        uow.rollback();

        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).rollback();
        inOrder.verify(mockConnection).close();
    }

    @Test
    public void testInnerUnitOfWorkCommitDoesNotCloseConnection() throws SQLException {
        when(mockConnection.getAutoCommit()).thenReturn(false);
        when(mockConnection.isClosed()).thenReturn(false);

        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        Connection actualOuter = testSubject.getConnection();

        verify(mockConnectionProvider, times(1)).getConnection();

        DefaultUnitOfWork<Message<?>> innerUow = DefaultUnitOfWork.startAndGet(null);
        Connection actualInner = testSubject.getConnection();

        verify(mockConnectionProvider, times(1)).getConnection();

        assertSame(actualOuter, actualInner);

        actualInner.close();
        actualInner.commit();

        verify(mockConnection, never()).commit();
        verify(mockConnection, never()).close();

        innerUow.commit();

        verify(mockConnection, never()).commit();
        verify(mockConnection, never()).close();

        uow.commit();

        verify(mockConnection).commit();
        verify(mockConnection).close();
    }
}
