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

package org.axonframework.auditing;

import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StubAggregate;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.util.AxonConfigurationException;
import org.junit.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AuditingInterceptorTest {

    private AuditingInterceptor testSubject;
    private AuditDataProvider mockAuditDataProvider;
    private AuditLogger mockAuditLogger;
    private InterceptorChain mockInterceptorChain;

    @Before
    public void setUp() {
        testSubject = new AuditingInterceptor();
        mockAuditDataProvider = mock(AuditDataProvider.class);
        mockAuditLogger = mock(AuditLogger.class);
        mockInterceptorChain = mock(InterceptorChain.class);

        testSubject.setAuditDataProvider(mockAuditDataProvider);
        testSubject.setAuditLogger(mockAuditLogger);

        when(mockAuditDataProvider.provideAuditDataFor(any(Object.class)))
                .thenReturn(Collections.singletonMap("key", (Serializable) "value"));
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testInterceptCommand_SuccessfulExecution() throws Throwable {
        when(mockInterceptorChain.proceed())
                .thenReturn("Return value");
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        aggregate.doSomething();
        uow.registerAggregate(aggregate, mock(SaveAggregateCallback.class));
        Object result = testSubject.handle("Command!", mockInterceptorChain);

        assertEquals("Return value", result);
        verify(mockAuditDataProvider, never()).provideAuditDataFor(any(Object.class));
        uow.commit();

        verify(mockAuditDataProvider, times(1)).provideAuditDataFor("Command!");
        verify(mockAuditLogger, times(1)).append(eq("Command!"), any(List.class));
        DomainEvent eventFromAggregate = aggregate.getUncommittedEvents().next();
        assertEquals("value", eventFromAggregate.getMetaDataValue("key"));
    }

    @Test
    public void testInterceptCommand_FailedExecution() throws Throwable {
        RuntimeException mockException = new RuntimeException("Mock");
        when(mockInterceptorChain.proceed())
                .thenThrow(mockException);
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        aggregate.doSomething();
        uow.registerAggregate(aggregate, mock(SaveAggregateCallback.class));
        try {
            testSubject.handle("Command!", mockInterceptorChain);
        } catch (RuntimeException e) {
            assertSame(mockException, e);
        }

        verify(mockAuditDataProvider, never()).provideAuditDataFor(any(Object.class));
        uow.rollback();
        verify(mockAuditDataProvider, never()).provideAuditDataFor(any(Object.class));
        verify(mockAuditLogger, never()).append(eq("Command!"), any(List.class));
    }

    @Test(expected = AxonConfigurationException.class)
    public void testInterceptorRaisesExceptionOnConfigurationError() throws Throwable {
        testSubject.handle(null, null);
    }
}
