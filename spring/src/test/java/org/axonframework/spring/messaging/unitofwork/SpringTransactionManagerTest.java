/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.messaging.unitofwork;

import org.axonframework.messaging.interceptors.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

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
        testSubject.startTransaction().commit();

        verify(transactionManager).getTransaction(transactionDefinition);
        verify(transactionManager).commit(underlyingTransactionStatus);
    }

    @Test
    public void testManageTransaction_DefaultTransactionStatus() {
        testSubject.startTransaction().commit();

        verify(transactionManager).getTransaction(isA(DefaultTransactionDefinition.class));
        verify(transactionManager).commit(underlyingTransactionStatus);
    }

    @Test
    public void testCommitTransaction_NoCommitOnInactiveTransaction() {
        Transaction transaction = testSubject.startTransaction();
        when(underlyingTransactionStatus.isCompleted()).thenReturn(true);
        transaction.commit();

        verify(transactionManager, never()).commit(underlyingTransactionStatus);
    }

    @Test
    public void testCommitTransaction_NoRollbackOnInactiveTransaction() {
        Transaction transaction = testSubject.startTransaction();
        when(underlyingTransactionStatus.isCompleted()).thenReturn(true);
        transaction.rollback();

        verify(transactionManager, never()).rollback(underlyingTransactionStatus);
    }

    @Test
    public void testCommitTransaction_NoCommitOnNestedTransaction() {
        Transaction transaction = testSubject.startTransaction();
        when(underlyingTransactionStatus.isNewTransaction()).thenReturn(false);
        transaction.commit();

        verify(transactionManager, never()).commit(underlyingTransactionStatus);
    }

    @Test
    public void testCommitTransaction_NoRollbackOnNestedTransaction() {
        Transaction transaction = testSubject.startTransaction();
        when(underlyingTransactionStatus.isNewTransaction()).thenReturn(false);
        transaction.rollback();

        verify(transactionManager, never()).rollback(underlyingTransactionStatus);
    }
}
