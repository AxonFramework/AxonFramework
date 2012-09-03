/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.*;
import org.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

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
    public void testManageTransaction_CustomTransactionStatus() {
        final TransactionDefinition transactionDefinition = mock(TransactionDefinition.class);
        testSubject.setTransactionDefinition(transactionDefinition);
        UnitOfWork transaction = testSubject.startTransaction();
        testSubject.commitTransaction(transaction);

        verify(transactionManager).getTransaction(transactionDefinition);
        verify(transactionManager).commit(underlyingTransactionStatus);
    }

    @Test
    public void testManageTransaction_DefaultTransactionStatus() {
        UnitOfWork transaction = testSubject.startTransaction();
        testSubject.commitTransaction(transaction);

        verify(transactionManager).getTransaction(isA(DefaultTransactionDefinition.class));
        verify(transactionManager).commit(underlyingTransactionStatus);
    }

    @Test
    public void testCleanupOfInnerUnitOfWorkInvokedAfterTxCommit() {
        UnitOfWork transaction = testSubject.startTransaction();
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        UnitOfWorkListener mockUoWListener = mock(UnitOfWorkListener.class);
        uow.registerListener(mockUoWListener);
        uow.commit();
        verify(mockUoWListener, never()).onCleanup(isA(UnitOfWork.class));
        testSubject.commitTransaction(transaction);

        InOrder inOrder = inOrder(mockUoWListener, transactionManager);

        inOrder.verify(mockUoWListener).onPrepareCommit(isA(UnitOfWork.class), isA(Set.class), isA(List.class));
        inOrder.verify(mockUoWListener).afterCommit(isA(UnitOfWork.class));
        inOrder.verify(transactionManager).commit(underlyingTransactionStatus);
        inOrder.verify(mockUoWListener).onCleanup(isA(UnitOfWork.class));
    }
}
