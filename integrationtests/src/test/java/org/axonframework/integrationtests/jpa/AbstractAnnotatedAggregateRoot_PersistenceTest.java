/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.jpa;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.repository.GenericJpaRepository;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.util.jpa.SimpleEntityManagerProvider;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@Transactional
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/infrastructure-context.xml")
public class AbstractAnnotatedAggregateRoot_PersistenceTest {

    @PersistenceContext
    private EntityManager entityManager;
    private AggregateIdentifier id = new UUIDAggregateIdentifier();
    @Autowired
    private EventBus eventBus;

    @Test
    public void testSaveAndLoadAggregate() {
        SimpleJpaEventSourcedAggregate aggregate = new SimpleJpaEventSourcedAggregate(id);
        aggregate.doSomething();
        aggregate.doSomething();

        aggregate.commitEvents();
        entityManager.persist(aggregate);
        entityManager.flush();

        SimpleJpaEventSourcedAggregate reloaded = entityManager.find(SimpleJpaEventSourcedAggregate.class,
                                                                     id.asString());
        assertEquals(id, reloaded.getIdentifier());
        assertEquals(2, reloaded.getInvocationCount());
        assertEquals((Long) 1L, reloaded.getVersion());

        reloaded.doSomething();

        assertEquals(3, reloaded.getInvocationCount());
        assertEquals((Long) 2L, reloaded.getUncommittedEvents().next().getSequenceNumber());
    }

    @Test
    public void testEventsPublishedCorrectly() {
        EventListener mockListener = mock(EventListener.class);
        GenericJpaRepository<SimpleJpaEventSourcedAggregate> repository = new GenericJpaRepository<SimpleJpaEventSourcedAggregate>(
                new SimpleEntityManagerProvider(entityManager),
                SimpleJpaEventSourcedAggregate.class);
        repository.setEventBus(eventBus);

        try {
            eventBus.subscribe(mockListener);

            DefaultUnitOfWork.startAndGet();
            SimpleJpaEventSourcedAggregate aggregate = new SimpleJpaEventSourcedAggregate(id);
            aggregate.doNothing();
            aggregate.doNothing();
            repository.add(aggregate);
            CurrentUnitOfWork.commit();

            verify(mockListener, times(0)).handle(isA(SomeEvent.class));
            verify(mockListener, times(2)).handle(isA(NothingEvent.class));

        } finally {
            eventBus.unsubscribe(mockListener);
        }
    }
}
