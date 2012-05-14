/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringTransactionalInterceptorTest {

    private PlatformTransactionManager mockTransactionManager;
    private TransactionStatus mockTransactionStatus;
    private SimpleCommandBus commandBus;
    private CommandHandler commandHandler;

    @SuppressWarnings({"unchecked"})
    @Before
    public void setUp() {
        mockTransactionStatus = mock(TransactionStatus.class);
        mockTransactionManager = mock(PlatformTransactionManager.class);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class))).thenReturn(mockTransactionStatus);
        when(mockTransactionStatus.isNewTransaction()).thenReturn(true);
        SpringTransactionalInterceptor testSubject = new SpringTransactionalInterceptor();
        testSubject.setTransactionManager(mockTransactionManager);
        commandBus = new SimpleCommandBus();
        commandBus.setInterceptors(Arrays.asList(testSubject));
        commandHandler = mock(CommandHandler.class);
        commandBus.subscribe(Object.class, commandHandler);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testTransactionManagement_CommitFails() {
        doThrow(new MockException()).when(mockTransactionManager).commit(mockTransactionStatus);
        when(mockTransactionStatus.isCompleted()).thenReturn(true);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new Object()));
        verify(mockTransactionManager).commit(mockTransactionStatus);
        verify(mockTransactionManager, never()).rollback(any(TransactionStatus.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testTransactionManagement_SuccessfulExecution() throws Throwable {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new Object()));
        InOrder inOrder = inOrder(mockTransactionManager, commandHandler);
        inOrder.verify(mockTransactionManager).getTransaction(isA(TransactionDefinition.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
        inOrder.verify(mockTransactionManager).commit(mockTransactionStatus);
        verifyNoMoreInteractions(mockTransactionManager, commandHandler);
    }

    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    @Test
    public void testTransactionManagement_RuntimeException() throws Throwable {
        final RuntimeException exception = new MockException();
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class))).thenThrow(exception);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new Object()), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Exception should be propagated");
            }

            @Override
            public void onFailure(Throwable actual) {
                assertSame(exception, actual);
            }
        });
        InOrder inOrder = inOrder(mockTransactionManager, commandHandler);
        inOrder.verify(mockTransactionManager).getTransaction(isA(TransactionDefinition.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
        inOrder.verify(mockTransactionManager).rollback(mockTransactionStatus);
        verifyNoMoreInteractions(mockTransactionManager, commandHandler);
    }
}
