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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.common.MatcherUtils.isEventWith;
import static org.junit.Assert.*;
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
    private Cluster cluster = new SimpleCluster("cluster");

    @Before
    public void prepareUnitOfWork() {
        while (CurrentUnitOfWork.isStarted()) {
            System.out.println(
                    "Warning! EventCountSnapshotterTriggerTest was started while an active UnitOfWork was present");
            CurrentUnitOfWork.get().rollback();
        }
        eventListener = mock(EventListener.class);
        unitOfWork = DefaultUnitOfWork.startAndGet(null);
        eventBus.subscribe(cluster);
        cluster.subscribe(eventListener);
        Mockito.reset(eventStore, eventListener);
    }

    @After
    public void clearUnitOfWork() {
        try {
            if (unitOfWork.isActive()) {
                unitOfWork.rollback();
            }
        } finally {
            while (CurrentUnitOfWork.isStarted()) {
                CurrentUnitOfWork.get().rollback();
                System.out.println(
                        "Warning!! EventCountSnapshotterTriggerTest seems to no correctly close all UnitOfWork");
            }
        }
    }

    @Test
    public void testStoreAggregate() {
        repository.setEventStore(eventStore);
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate(UUID.randomUUID().toString());
        aggregate.increaseCounter();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        entityManager.flush();
        entityManager.clear();

        verify(eventStore).appendEvents(streamContaining(1L));
        verify(eventListener).handle(isA(DomainEventMessage.class));
        assertNotNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier()));
    }

    @Test
    public void testStoreAggregate_NoEventStore() {
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate("id");
        aggregate.increaseCounter();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        verify(eventListener).handle(isA(DomainEventMessage.class));

        entityManager.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier()));
    }

    @Test
    public void testDeleteAggregate() {
        repository.setEventStore(eventStore);
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate("id");
        aggregate.increaseCounter();
        aggregate.delete();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        entityManager.flush();
        entityManager.clear();

        verify(eventStore).appendEvents(streamContaining(2L));
        assertNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier()));
        verify(eventListener).handle(isEventWith(StubDomainEvent.class));
        verify(eventListener).handle(isEventWith(JpaEventSourcedAggregate.MyAggregateDeletedEvent.class));
    }

    @Test
    public void testDeleteAggregate_NoEventStore() {
        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate("id");
        aggregate.increaseCounter();
        aggregate.delete();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();

        verify(eventListener).handle(isEventWith(StubDomainEvent.class));
        verify(eventListener).handle(isEventWith(JpaEventSourcedAggregate.MyAggregateDeletedEvent.class));

        entityManager.flush();
        entityManager.clear();

        assertNull(entityManager.find(JpaEventSourcedAggregate.class, aggregate.getIdentifier()));
    }

    @Test
    public void testLoadAggregate() {
        repository.setEventStore(eventStore);

        JpaEventSourcedAggregate aggregate = new JpaEventSourcedAggregate("id");
        aggregate.increaseCounter();
        aggregate.commitEvents();
        entityManager.persist(aggregate);

        JpaEventSourcedAggregate reloaded = repository.load(aggregate.getIdentifier());
        assertNotNull(reloaded);
        assertEquals((Long) 0L, aggregate.getVersion());
        verifyNoMoreInteractions(eventStore);
    }

    private List<DomainEventMessage<?>> streamContaining(final long expectedCount) {
        return argThat(new TypeSafeMatcher<List<DomainEventMessage<?>>>() {

            @Override
            protected boolean matchesSafely(List<DomainEventMessage<?>> item) {
                return item.size() == expectedCount;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("DomainEvents array containing");
                description.appendValue(expectedCount);
                description.appendText("events");
            }
        });
    }
}
