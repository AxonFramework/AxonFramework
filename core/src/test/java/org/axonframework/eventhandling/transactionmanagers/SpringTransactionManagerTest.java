/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.eventhandling.transactionmanagers;

import org.axonframework.eventhandling.RetryPolicy;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.*;
import org.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringTransactionManagerTest {

    private SpringTransactionManager testSubject;
    private PlatformTransactionManager transactionManager;
    private org.springframework.transaction.TransactionStatus underlyingTransactionStatus;

    @Before
    public void setUp() {
        underlyingTransactionStatus = mock(org.springframework.transaction.TransactionStatus.class);
        transactionManager = mock(PlatformTransactionManager.class);
        testSubject = new SpringTransactionManager();
        testSubject.setTransactionManager(transactionManager);
        when(transactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(underlyingTransactionStatus);
        when(underlyingTransactionStatus.isNewTransaction()).thenReturn(true);
    }

    @Test
    public void testManageTransaction_RegularFlow() {
        TransactionStatus mockTransactionStatus = mock(TransactionStatus.class);
        testSubject.beforeTransaction(mockTransactionStatus);
        when(mockTransactionStatus.isSuccessful()).thenReturn(true);
        testSubject.afterTransaction(mockTransactionStatus);

        verify(transactionManager).getTransaction(isA(TransactionDefinition.class));
        verify(transactionManager).commit(underlyingTransactionStatus);
    }

    @Test
    public void testManageTransaction_TransientException() {
        TransactionStatus mockTransactionStatus = mock(TransactionStatus.class);
        testSubject.beforeTransaction(mockTransactionStatus);
        when(mockTransactionStatus.isSuccessful()).thenReturn(false);
        when(mockTransactionStatus.getException()).thenReturn(
                new RuntimeException("Wrapper around a transient exception",
                                     new SQLTimeoutException("Faking a timeout")));
        testSubject.afterTransaction(mockTransactionStatus);

        InOrder inOrder = inOrder(transactionManager, mockTransactionStatus);
        inOrder.verify(transactionManager).getTransaction(isA(TransactionDefinition.class));
        inOrder.verify(mockTransactionStatus).setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
        inOrder.verify(transactionManager).rollback(underlyingTransactionStatus);
    }

    @Test
    public void testManageTransaction_NonTransientException() {
        TransactionStatus mockTransactionStatus = mock(TransactionStatus.class);
        testSubject.beforeTransaction(mockTransactionStatus);
        when(mockTransactionStatus.isSuccessful()).thenReturn(false);
        when(mockTransactionStatus.getException()).thenReturn(new RuntimeException("Faking a timeout"));
        testSubject.afterTransaction(mockTransactionStatus);

        InOrder inOrder = inOrder(transactionManager, mockTransactionStatus);
        inOrder.verify(transactionManager).getTransaction(isA(TransactionDefinition.class));
        inOrder.verify(mockTransactionStatus).setRetryPolicy(RetryPolicy.SKIP_FAILED_EVENT);
        inOrder.verify(transactionManager).commit(underlyingTransactionStatus);
    }

    @Test
    public void testCleanupOfInnerUnitOfWorkInvokedAfterTxCommit() {
        TransactionStatus mockTransactionStatus = mock(TransactionStatus.class);
        testSubject.beforeTransaction(mockTransactionStatus);
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        UnitOfWorkListener mockUoWListener = mock(UnitOfWorkListener.class);
        uow.registerListener(mockUoWListener);
        uow.commit();
        verify(mockUoWListener, never()).onCleanup();
        when(mockTransactionStatus.isSuccessful()).thenReturn(true);
        testSubject.afterTransaction(mockTransactionStatus);

        InOrder inOrder = inOrder(mockUoWListener, transactionManager);

        inOrder.verify(mockUoWListener).onPrepareCommit(isA(Set.class), isA(List.class));
        inOrder.verify(mockUoWListener).afterCommit();
        inOrder.verify(transactionManager).commit(underlyingTransactionStatus);
        inOrder.verify(mockUoWListener).onCleanup();
    }
}
