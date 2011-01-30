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

package org.axonframework.repository;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.*;

/**
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/META-INF/spring/jpa-repository-context.xml",
        "/META-INF/spring/db-context.xml"})
@Transactional
public class JpaRepositoryIntegrationTest implements EventListener {

    @Autowired
    @Qualifier("simpleRepository")
    private GenericJpaRepository<JpaAggregate> repository;

    @Autowired
    private EventBus eventBus;

    @PersistenceContext
    private EntityManager entityManager;

    private List<DomainEvent> capturedEvents;

    @Before
    public void setUp() {
        capturedEvents = new ArrayList<DomainEvent>();
        eventBus.subscribe(this);
    }

    @Test
    public void testStoreAndLoadNewAggregate() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        JpaAggregate originalAggregate = new JpaAggregate("Hello");
        repository.add(originalAggregate);
        uow.commit();

        entityManager.flush();
        entityManager.clear();
        List results = entityManager.createQuery("SELECT a FROM JpaAggregate a").getResultList();
        assertEquals(1, results.size());

        uow = DefaultUnitOfWork.startAndGet();
        JpaAggregate storedAggregate = repository.load(originalAggregate.getIdentifier());
        uow.commit();
        assertEquals(storedAggregate.getIdentifier(), originalAggregate.getIdentifier());
        assertEquals((Long) 0L, originalAggregate.getVersion());
        assertTrue(capturedEvents.isEmpty());
    }

    @Test
    public void testUpdateAnAggregate() {
        JpaAggregate agg = new JpaAggregate();
        entityManager.persist(agg);
        entityManager.flush();
        entityManager.clear();

        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        JpaAggregate aggregate = repository.load(agg.getIdentifier());
        aggregate.setMessage("And again");
        aggregate.setMessage("And more");
        uow.commit();
        entityManager.flush();
        assertEquals((Long) 1L, aggregate.getLastEventSequenceNumber());
        assertEquals((Long) 1L, aggregate.getVersion());
        Assert.assertEquals(2, capturedEvents.size());
        Assert.assertEquals((Long) 0L, capturedEvents.get(0).getSequenceNumber());
        Assert.assertEquals((Long) 1L, capturedEvents.get(1).getSequenceNumber());
    }

    @Override
    public void handle(Event event) {
        if (DomainEvent.class.isInstance(event)) {
            this.capturedEvents.add((DomainEvent) event);
        }
    }
}
