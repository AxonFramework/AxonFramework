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

package org.axonframework.repository;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.unitofwork.CurrentUnitOfWork;
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

    private List<EventMessage> capturedEvents;
    private Cluster cluster = new SimpleCluster("test");


    @Before
    public void setUp() {
        capturedEvents = new ArrayList<>();
        eventBus.subscribe(cluster);
        cluster.subscribe(this);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testStoreAndLoadNewAggregate() {
        UnitOfWork uow = startAndGetUnitOfWork();
        JpaAggregate originalAggregate = new JpaAggregate("Hello");
        repository.add(originalAggregate);
        uow.commit();

        entityManager.flush();
        entityManager.clear();
        List<JpaAggregate> results = entityManager.createQuery("SELECT a FROM JpaAggregate a").getResultList();
        assertEquals(1, results.size());
        JpaAggregate aggregate = results.get(0);
        assertEquals(originalAggregate.getIdentifier(), aggregate.getIdentifier());

        uow = startAndGetUnitOfWork();
        JpaAggregate storedAggregate = repository.load(originalAggregate.getIdentifier());
        uow.commit();
        assertEquals(storedAggregate.getIdentifier(), originalAggregate.getIdentifier());
        assertEquals((Long) 0L, originalAggregate.getVersion());
        assertTrue(capturedEvents.isEmpty());
    }

    @Test
    public void testUpdateAnAggregate() {
        JpaAggregate agg = new JpaAggregate("First message");
        entityManager.persist(agg);
        entityManager.flush();
        entityManager.clear();
        assertEquals((Long) 0L, agg.getVersion());

        UnitOfWork uow = startAndGetUnitOfWork();
        JpaAggregate aggregate = repository.load(agg.getIdentifier());
        aggregate.setMessage("And again");
        aggregate.setMessage("And more");
        uow.commit();

        assertEquals((Long) 1L, aggregate.getVersion());
        assertEquals(2, capturedEvents.size());
        assertNotNull(entityManager.find(JpaAggregate.class, aggregate.getIdentifier()));
    }

    @Test
    public void testDeleteAnAggregate() {
        JpaAggregate agg = new JpaAggregate("First message");
        entityManager.persist(agg);
        entityManager.flush();
        entityManager.clear();
        assertEquals((Long) 0L, agg.getVersion());

        UnitOfWork uow = startAndGetUnitOfWork();
        JpaAggregate aggregate = repository.load(agg.getIdentifier());
        aggregate.setMessage("And again");
        aggregate.setMessage("And more");
        aggregate.delete();
        uow.commit();
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, capturedEvents.size());
        assertNull(entityManager.find(JpaAggregate.class, aggregate.getIdentifier()));
    }

    @Override
    public void handle(EventMessage event) {
        this.capturedEvents.add(event);
    }

    private UnitOfWork startAndGetUnitOfWork() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);
        uow.resources().put(EventBus.KEY, eventBus);
        return uow;
    }
}
