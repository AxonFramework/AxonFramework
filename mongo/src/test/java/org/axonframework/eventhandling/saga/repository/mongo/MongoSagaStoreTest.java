/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.saga.repository.mongo;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventhandling.saga.AssociationValuesImpl;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventsourcing.eventstore.mongo.MongoEventStorageEngine;
import org.axonframework.mongoutils.MongoLauncher;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.junit.Assert.*;

/**
 * @author Jettro Coenradie
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/mongo-context.xml")
public class MongoSagaStoreTest {

    private final static Logger logger = LoggerFactory.getLogger(MongoSagaStoreTest.class);
    private static MongodProcess mongod;
    private static MongodExecutable mongoExe;

    @Autowired
    private MongoSagaStore sagaStore;

    @Autowired
    @Qualifier("sagaMongoTemplate")
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationContext context;

    @BeforeClass
    public static void start() throws IOException {
        mongoExe = MongoLauncher.prepareExecutable();
        mongod = mongoExe.start();
    }

    @AfterClass
    public static void shutdown() {
        if (mongod != null) {
            mongod.stop();
        }
        if (mongoExe != null) {
            mongoExe.stop();
        }
    }

    @Before
    public void setUp() throws Exception {
        try {
            context.getBean(Mongo.class);
            context.getBean(MongoEventStorageEngine.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate.sagaCollection().drop();
    }

    @DirtiesContext
    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaFound() {
        MyTestSaga testSaga = new MyTestSaga();
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        sagaStore.insertSaga(MyTestSaga.class, "test1", testSaga, null, singleton(associationValue));
        sagaStore.insertSaga(MyTestSaga.class, "test2", otherTestSaga, null, singleton(associationValue));
        Set<String> actual = sagaStore.findSagas(MyTestSaga.class, associationValue);
        assertEquals(1, actual.size());
        assertEquals(MyTestSaga.class, sagaStore.loadSaga(MyTestSaga.class, actual.iterator().next()).saga().getClass());

        Set<String> actual2 = sagaStore.findSagas(MyOtherTestSaga.class, associationValue);
        assertEquals(1, actual2.size());
        assertEquals(MyOtherTestSaga.class, sagaStore.loadSaga(MyOtherTestSaga.class, actual2.iterator().next()).saga().getClass());

        DBObject sagaQuery = SagaEntry.queryByIdentifier("test1");
        DBCursor sagaCursor = mongoTemplate.sagaCollection().find(sagaQuery);
        assertEquals("Amount of found sagas is not as expected", 1, sagaCursor.size());
    }

    @DirtiesContext
    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_NoSagaFound() {
        MyTestSaga testSaga = new MyTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga();
        sagaStore.insertSaga(MyTestSaga.class, "test1", testSaga, null, singleton(associationValue));
        sagaStore.insertSaga(MyTestSaga.class, "test2", otherTestSaga, null, singleton(associationValue));
        Set<String> actual = sagaStore.findSagas(InexistentSaga.class, new AssociationValue("key", "value"));
        assertTrue("Didn't expect any sagas", actual.isEmpty());
    }

    @Test
    @DirtiesContext
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaDeleted() {
        MyTestSaga testSaga = new MyTestSaga();
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        sagaStore.insertSaga(MyTestSaga.class, "test1", testSaga, null, singleton(associationValue));

        sagaStore.insertSaga(MyTestSaga.class, "test2", otherTestSaga, null, singleton(associationValue));
        sagaStore.deleteSaga(MyTestSaga.class, "test1", singleton(associationValue));
        Set<String> actual = sagaStore.findSagas(MyTestSaga.class, associationValue);
        assertTrue("Didn't expect any sagas", actual.isEmpty());

        DBObject sagaQuery = SagaEntry.queryByIdentifier("test1");
        DBCursor sagaCursor = mongoTemplate.sagaCollection().find(sagaQuery);
        assertEquals("No saga is expected after .end and .commit", 0, sagaCursor.size());
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByIdentifier() {
        MyTestSaga saga = new MyTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        sagaStore.insertSaga(MyTestSaga.class, "test1", saga, null, singleton(associationValue));
        SagaStore.Entry<MyTestSaga> loaded = sagaStore.loadSaga(MyTestSaga.class, "test1");
        assertEquals(singleton(associationValue), loaded.associationValues());
        assertEquals(MyTestSaga.class, loaded.saga().getClass());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier("test1")));
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByAssociationValue() {
        MyTestSaga saga = new MyTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        sagaStore.insertSaga(MyTestSaga.class, "test1", saga, null, singleton(associationValue));
        Set<String> loaded = sagaStore.findSagas(MyTestSaga.class, associationValue);
        assertEquals(1, loaded.size());
        SagaStore.Entry<MyTestSaga> loadedSaga = sagaStore.loadSaga(MyTestSaga.class, loaded.iterator().next());
        assertEquals(singleton(associationValue), loadedSaga.associationValues());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier("test1")));
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    @DirtiesContext
    public void testAddAndLoadSaga_MultipleHitsByAssociationValue() {
        String identifier1 = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        MyTestSaga saga1 = new MyTestSaga();
        MyOtherTestSaga saga2 = new MyOtherTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        sagaStore.insertSaga(MyTestSaga.class, identifier1, saga1, null, singleton(associationValue));
        sagaStore.insertSaga(MyOtherTestSaga.class, identifier2, saga2, null, singleton(associationValue));

        // load saga1
        Set<String> loaded1 = sagaStore.findSagas(MyTestSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, loaded1.size());
        SagaStore.Entry<MyTestSaga> loadedSaga1 = sagaStore.loadSaga(MyTestSaga.class, loaded1.iterator().next());
        assertEquals(singleton(associationValue), loadedSaga1.associationValues());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier1)));

        // load saga2
        Set<String> loaded2 = sagaStore.findSagas(MyOtherTestSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, loaded2.size());
        SagaStore.Entry<MyOtherTestSaga> loadedSaga2 = sagaStore.loadSaga(MyOtherTestSaga.class, loaded2.iterator().next());
        assertEquals(singleton(associationValue), loadedSaga2.associationValues());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier2)));
    }

    @Test
    @DirtiesContext
    public void testAddAndLoadSaga_AssociateValueAfterStorage() {
        AssociationValue associationValue = new AssociationValue("key", "value");
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga();
        sagaStore.insertSaga(MyTestSaga.class, identifier, saga, null, singleton(associationValue));

        Set<String> loaded = sagaStore.findSagas(MyTestSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, loaded.size());
        SagaStore.Entry<MyTestSaga> loadedSaga = sagaStore.loadSaga(MyTestSaga.class, loaded.iterator().next());
        assertEquals(singleton(associationValue), loadedSaga.associationValues());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier)));
    }

    @Test
    public void testLoadSaga_NotFound() {
        assertNull(sagaStore.loadSaga(MyTestSaga.class, "123456"));
    }

    @DirtiesContext
    @Test
    public void testLoadSaga_AssociationValueRemoved() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        mongoTemplate.sagaCollection().save(new SagaEntry<>(identifier, saga, singleton(associationValue),
                                                          new XStreamSerializer()).asDBObject());

        SagaStore.Entry<MyTestSaga> loaded = sagaStore.loadSaga(MyTestSaga.class, identifier);
        AssociationValues av = new AssociationValuesImpl(loaded.associationValues());
        av.remove(associationValue);
        sagaStore.updateSaga(MyTestSaga.class, identifier, loaded.saga(), null, av);
        Set<String> found = sagaStore.findSagas(MyTestSaga.class, new AssociationValue("key", "value"));
        assertEquals(0, found.size());
    }

    @DirtiesContext
    @Test
    public void testSaveSaga() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga();
        XStreamSerializer serializer = new XStreamSerializer();
        mongoTemplate.sagaCollection().save(new SagaEntry<>(identifier, saga, emptySet(), serializer).asDBObject());
        SagaStore.Entry<MyTestSaga> loaded = sagaStore.loadSaga(MyTestSaga.class, identifier);
        loaded.saga().counter = 1;
        sagaStore.updateSaga(MyTestSaga.class, identifier, loaded.saga(), null, new AssociationValuesImpl(loaded.associationValues()));

        SagaEntry entry = new SagaEntry(mongoTemplate.sagaCollection().findOne(SagaEntry
                                                                                       .queryByIdentifier(identifier)));
        MyTestSaga actualSaga = (MyTestSaga) entry.getSaga(serializer);
        assertNotSame(loaded, actualSaga);
        assertEquals(1, actualSaga.counter);
    }

    @DirtiesContext
    @Test
    public void testEndSaga() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        mongoTemplate.sagaCollection().save(new SagaEntry<>(identifier, saga, singleton(associationValue), new XStreamSerializer()).asDBObject());
        sagaStore.deleteSaga(MyTestSaga.class, identifier, singleton(associationValue));

        assertNull(mongoTemplate.sagaCollection().findOne(SagaEntry.queryByIdentifier(identifier)));
    }

    public static class MyTestSaga {

        private static final long serialVersionUID = -1562911263884220240L;
        private int counter = 0;

    }

    public static class MyOtherTestSaga {

    }

    private class InexistentSaga {

    }
}
