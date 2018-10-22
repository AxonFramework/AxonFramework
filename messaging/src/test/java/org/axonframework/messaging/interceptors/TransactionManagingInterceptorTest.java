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

package org.axonframework.messaging.interceptors;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class TransactionManagingInterceptorTest {

    private Message<?> message;
    private InterceptorChain interceptorChain;
    private UnitOfWork<Message<?>> unitOfWork;
    private TransactionManager transactionManager;
    private Transaction transaction;
    private TransactionManagingInterceptor<Message<?>> subject;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        interceptorChain = mock(InterceptorChain.class);
        message = new GenericMessage<>(new Object());
        unitOfWork = DefaultUnitOfWork.startAndGet(message);
        transactionManager = mock(TransactionManager.class);
        transaction = mock(Transaction.class);
        when(transactionManager.startTransaction()).thenReturn(transaction);
        subject = new TransactionManagingInterceptor<>(transactionManager);
    }

    @Test
    public void testStartTransaction() throws Exception {
        UnitOfWork<Message<?>> unitOfWork = spy(this.unitOfWork);

        subject.handle(unitOfWork, interceptorChain);
        verify(transactionManager).startTransaction();
        verify(interceptorChain).proceed();

        verify(unitOfWork).onCommit(any());
        verify(unitOfWork).onRollback(any());
    }

    @Test
    public void testUnitOfWorkCommit() throws Exception {
        subject.handle(unitOfWork, interceptorChain);
        unitOfWork.commit();

        verify(transaction).commit();
    }
}
