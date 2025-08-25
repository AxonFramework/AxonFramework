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

package org.axonframework.messaging.interceptors;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link TransactionManagingInterceptor}.
 *
 * @author Rene de Waele
 */
@Disabled("TODO ")
class TransactionManagingInterceptorTest {

    private MessageHandlerInterceptorChain<Message<?>> interceptorChain;
    private TransactionManager transactionManager;
    private Transaction transaction;
    private TransactionManagingInterceptor<Message<?>> subject;
    private ProcessingContext context;
    private Message<?> message;

    @BeforeEach
    void setUp() {
        interceptorChain = mock();
        message = new GenericMessage<>(new MessageType("message"), new Object());
        context = StubProcessingContext.forMessage(message);
        transactionManager = mock(TransactionManager.class);
        transaction = mock(Transaction.class);
        when(transactionManager.startTransaction()).thenReturn(transaction);
        subject = new TransactionManagingInterceptor<>(transactionManager);
    }

    @Test
    void startTransaction() throws Exception {

        subject.interceptOnHandle(message, context, interceptorChain);
        verify(transactionManager).startTransaction();
        verify(interceptorChain).proceed(message, context);
    }

    @Test
    void commit() throws Exception {
        subject.interceptOnHandle(message, context, interceptorChain);
        verify(transaction).commit();
        verify(interceptorChain).proceed(message, context);
    }

    @Test
    void rollback() throws Exception {
        subject.interceptOnHandle(message, context, interceptorChain);
        verify(transaction).rollback();
        verify(interceptorChain).proceed(message, context);
    }

}
