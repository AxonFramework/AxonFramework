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

package org.axonframework.repository;

import org.axonframework.domain.AbstractJpaAggregateRoot;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.junit.*;

import javax.persistence.EntityManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 */
public class GenericJpaRepositoryTest {

    private EntityManager mockEntityManager;
    private GenericJpaRepository<StubJpaAggregate> testSubject;
    private AggregateIdentifier aggregateId;
    private StubJpaAggregate aggregate;

    @Before
    public void setUp() {
        mockEntityManager = mock(EntityManager.class);
        testSubject = new GenericJpaRepository<StubJpaAggregate>(StubJpaAggregate.class);
        testSubject.setEntityManager(mockEntityManager);
        DefaultUnitOfWork.startAndGet();
        aggregateId = new StringAggregateIdentifier("123");
        aggregate = new StubJpaAggregate(aggregateId);
        when(mockEntityManager.find(StubJpaAggregate.class, "123")).thenReturn(aggregate);
    }

    @After
    public void cleanUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testLoadAggregate() {
        StubJpaAggregate actualResult = testSubject.load(aggregateId);
        assertSame(aggregate, actualResult);
    }

    @Test
    public void testPersistAggregate_DefaultFlushMode() {
        testSubject.doSaveWithLock(aggregate);
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    public void testPersistAggregate_ExplicitFlushModeOn() {
        testSubject.setForceFlushOnSave(true);
        testSubject.doSaveWithLock(aggregate);
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    public void testPersistAggregate_ExplicitFlushModeOff() {
        testSubject.setForceFlushOnSave(false);
        testSubject.doSaveWithLock(aggregate);
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager, never()).flush();
    }

    private class StubJpaAggregate extends AbstractJpaAggregateRoot {

        private StubJpaAggregate(AggregateIdentifier identifier) {
            super(identifier);
        }
    }
}
