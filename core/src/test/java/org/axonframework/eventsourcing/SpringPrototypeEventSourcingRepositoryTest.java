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

package org.axonframework.eventsourcing;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(locations = {"/contexts/SpringPrototypeEventSourcingRepositoryTest-context.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringPrototypeEventSourcingRepositoryTest {

    @Autowired
    @Qualifier("repositoryOne")
    private EventSourcingRepository repositoryWithDefaultIdentifier;

    @Autowired
    @Qualifier("repositoryOne")
    private EventSourcingRepository<StubAggregate> repository;

    @Autowired
    @Qualifier("repositoryTwo")
    private EventSourcingRepository repositoryWithExplicitIdentifier;

    @Autowired
    private EventStore mockEventStore;

    @Test
    public void testRepositoryTypeIdentifier() throws Exception {
        assertEquals("myStubAggregate", repositoryWithDefaultIdentifier.getTypeIdentifier());
        assertEquals("anotherTypeIdentifier", repositoryWithExplicitIdentifier.getTypeIdentifier());
    }

    @Test
    public void testCreateInstances() {
        try {
            new DefaultUnitOfWork().start();
            final AggregateIdentifier aggregateIdentifier1 = new UUIDAggregateIdentifier();
            final AggregateIdentifier aggregateIdentifier2 = new UUIDAggregateIdentifier();
            when(mockEventStore.readEvents(eq(repository.getTypeIdentifier()), isA(AggregateIdentifier.class)))
                    .thenAnswer(new Answer<Object>() {
                        @Override
                        public Object answer(InvocationOnMock invocation) throws Throwable {
                            return new SimpleDomainEventStream(
                                    new StubDomainEvent((AggregateIdentifier) invocation.getArguments()[1],
                                                        0L));
                        }
                    });
            StubAggregate aggregate1 = repository.load(aggregateIdentifier1, 0L);
            StubAggregate aggregate2 = repository.load(aggregateIdentifier2, 0L);

            assertNotSame(aggregate1, aggregate2);
            assertEquals(Long.valueOf(0L), aggregate1.getVersion());
            assertEquals(Long.valueOf(0L), aggregate2.getVersion());
        } finally {
            while (CurrentUnitOfWork.isStarted()) {
                CurrentUnitOfWork.get().rollback();
            }
        }
    }
}
