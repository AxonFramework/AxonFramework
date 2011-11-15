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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.common.MatcherUtils.isEventWith;
import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/META-INF/spring/db-context.xml",
        "classpath:/META-INF/spring/jpa-repository-context.xml"})
@Transactional
public class HybridJpaRepositoryTest {

    @Autowired
    private HybridJpaRepository<JpaEventSourcedAggregate> repository;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventBus eventBus;

    @PersistenceContext
    private EntityManager entityManager;

    private UnitOfWork unitOfWork;

    private EventListener eventListener;

    @Before
    public void prepateUnitOfWork() {
        eventListener = mock(EventListener.class);
        unitOfWork = DefaultUnitOfWork.startAndGet();
        eventBus.subscribe(eventListener);
        Mockito.reset(eventStore, eventListener);
    }

    @After
    public void clearUnitOfWork() {
        if (unitOfWork.isStarted()) {
            unitOfWork.rollback();
        }
    }

    @Test
    public void testStoreAggregate() {
        repository.setEventStore(eventStore);
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate();
        aggregate.increaseCounter();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        entityManager.flush();
        entityManager.clear();

        verify(eventStore).appendEvents(eq("JpaEventSourcedAggregate"), streamContaining(1L));
        verify(eventListener).handle(isA(DomainEventMessage.class));
        assertNotNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier().asString()));
    }

    @Test
    public void testStoreAggregate_NoEventStore() {
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate();
        aggregate.increaseCounter();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        verify(eventListener).handle(isA(DomainEventMessage.class));

        entityManager.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier().asString()));
    }

    @Test
    public void testDeleteAggregate() {
        repository.setEventStore(eventStore);
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate();
        aggregate.increaseCounter();
        aggregate.delete();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        entityManager.flush();
        entityManager.clear();

        verify(eventStore).appendEvents(eq("JpaEventSourcedAggregate"), streamContaining(2L));
        assertNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier().asString()));
        verify(eventListener).handle(isEventWith(StubDomainEvent.class));
        verify(eventListener).handle(isEventWith(JpaEventSourcedAggregate.MyAggregateDeletedEvent.class));
    }

    @Test
    public void testDeleteAggregate_NoEventStore() {
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate();
        aggregate.increaseCounter();
        aggregate.delete();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        verify(eventListener).handle(isEventWith(StubDomainEvent.class));
        verify(eventListener).handle(isEventWith(JpaEventSourcedAggregate.MyAggregateDeletedEvent.class));

        entityManager.flush();
        entityManager.clear();

        assertNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier().asString()));
    }

    @Test
    public void testLoadAggregate() {
        repository.setEventStore(eventStore);

        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate();
        aggregate.increaseCounter();
        aggregate.commitEvents();
        entityManager.persist(aggregate);

        JpaEventSourcedAggregate reloaded = repository.load(aggregate.getIdentifier());
        assertNotNull(reloaded);
        assertEquals((Long) 0L, aggregate.getVersion());
        verifyNoMoreInteractions(eventStore);
    }

    private DomainEventStream streamContaining(final long expectedCount) {
        return argThat(new BaseMatcher<DomainEventStream>() {

            private Long previousCount = null;

            @Override
            public boolean matches(Object o) {
                if (previousCount != null) {
                    return previousCount.equals(expectedCount);
                }
                long counter = 0;
                if (o instanceof DomainEventStream) {
                    DomainEventStream events = (DomainEventStream) o;
                    while (events.hasNext()) {
                        events.next();
                        counter++;
                    }
                }
                previousCount = counter;
                return counter == expectedCount;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("DomainEventStream containing");
                description.appendValue(expectedCount);
                description.appendText("events");
            }
        });
    }
}
