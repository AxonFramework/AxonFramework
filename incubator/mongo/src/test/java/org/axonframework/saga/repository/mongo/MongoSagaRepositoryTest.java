package org.axonframework.saga.repository.mongo;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import org.axonframework.domain.Event;
import org.axonframework.eventstore.mongo.MongoEventStore;
import org.axonframework.saga.*;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.JavaSagaSerializer;
import org.axonframework.saga.repository.XStreamSagaSerializer;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.axonframework.TestUtils.setOf;
import static org.junit.Assert.*;

/**
 * @author Jettro Coenradie
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/mongo-context.xml")
public class MongoSagaRepositoryTest {
    private final static Logger logger = LoggerFactory.getLogger(MongoSagaRepositoryTest.class);

    @Autowired
    private MongoSagaRepository repository;

    @Autowired
    @Qualifier("sagaMongoTemplate")
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationContext context;

    private Mongo mongo;
    private MongoEventStore eventStore;

    @Before
    public void setUp() throws Exception {
        try {
            mongo = context.getBean(Mongo.class);
            eventStore = context.getBean(MongoEventStore.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate.database().dropDatabase();
    }

    @DirtiesContext
    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaFound() {
        MyTestSaga testSaga = new MyTestSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        Set<MyTestSaga> actual = repository.find(MyTestSaga.class, Collections.singleton(new AssociationValue("key",
                "value")));
        assertEquals(1, actual.size());
        assertEquals(MyTestSaga.class, actual.iterator().next().getClass());

        DBObject associationQuery = AssociationValueEntry.queryByKeyAndValue("key", "value");
        DBCursor associationCursor = mongoTemplate.associationsCollection().find(associationQuery);
        assertEquals("Amount of found associations is not as expected", 2, associationCursor.size());

        DBObject sagaQuery = SagaEntry.queryByIdentifier("test1");
        DBCursor sagaCursor = mongoTemplate.sagaCollection().find(sagaQuery);
        assertEquals("Amount of found sagas is not as expected", 1, sagaCursor.size());

    }

    @DirtiesContext
    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_NoSagaFound() {
        MyTestSaga testSaga = new MyTestSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        Set<InexistentSaga> actual = repository.find(InexistentSaga.class, Collections.singleton(new AssociationValue(
                "key",
                "value")));
        assertTrue("Didn't expect any sagas", actual.isEmpty());
    }

    @Test
    @DirtiesContext
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaDeleted() {
        MyTestSaga testSaga = new MyTestSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        testSaga.end(); // make the saga inactive
        repository.commit(testSaga); //remove the saga because it is inactive
        Set<MyTestSaga> actual = repository.find(MyTestSaga.class,
                Collections.singleton(new AssociationValue("key", "value")));
        assertTrue("Didn't expect any sagas", actual.isEmpty());

        DBObject sagaQuery = SagaEntry.queryByIdentifier("test1");
        DBCursor sagaCursor = mongoTemplate.sagaCollection().find(sagaQuery);
        assertEquals("No saga is expected after .end and .commit", 0, sagaCursor.size());
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByIdentifier() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        repository.add(saga);
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        assertEquals(identifier, loaded.getSagaIdentifier());
        assertSame(loaded, saga);
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier)));
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByAssociationValue() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(saga);
        Set<MyTestSaga> loaded = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded.size());
        MyTestSaga loadedSaga = loaded.iterator().next();
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertSame(loadedSaga, saga);
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier)));
    }

    @Test
    @DirtiesContext
    public void testAddAndLoadSaga_MultipleHitsByAssociationValue() {
        String identifier1 = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        MyTestSaga saga1 = new MyTestSaga(identifier1);
        MyOtherTestSaga saga2 = new MyOtherTestSaga(identifier2);
        saga1.registerAssociationValue(new AssociationValue("key", "value"));
        saga2.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(saga1);
        repository.add(saga2);

        // we attempt to force a cache cleanup to reproduce a problem found on production
        saga1 = null;
        saga2 = null;
        System.gc();
        repository.purgeCache();

        // load saga1
        Set<MyTestSaga> loaded1 = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded1.size());
        MyTestSaga loadedSaga1 = loaded1.iterator().next();
        assertEquals(identifier1, loadedSaga1.getSagaIdentifier());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier1)));

        // load saga2
        Set<MyOtherTestSaga> loaded2 = repository.find(MyOtherTestSaga.class, setOf(new AssociationValue("key",
                "value")));
        assertEquals(1, loaded2.size());
        MyOtherTestSaga loadedSaga2 = loaded2.iterator().next();
        assertEquals(identifier2, loadedSaga2.getSagaIdentifier());
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier2)));
    }

    @Test
    @DirtiesContext
    public void testAddAndLoadSaga_AssociateValueAfterStorage() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        repository.add(saga);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        Set<MyTestSaga> loaded = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded.size());
        MyTestSaga loadedSaga = loaded.iterator().next();
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertSame(loadedSaga, saga);
        assertNotNull(mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(identifier)));
    }

    @DirtiesContext
    @Test
    public void testLoadUncachedSaga_ByIdentifier() {
        repository.setSerializer(new XStreamSagaSerializer());
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        mongoTemplate.sagaCollection().save(new SagaEntry(saga, new XStreamSagaSerializer()).asDBObject());
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        assertNotSame(saga, loaded);
        assertEquals(identifier, loaded.getSagaIdentifier());
    }

    @Test(expected = NoSuchSagaException.class)
    public void testLoadSaga_NotFound() {
        repository.load(MyTestSaga.class, "123456");
    }

    @DirtiesContext
    @Test
    public void testLoadSaga_AssociationValueRemoved() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        mongoTemplate.sagaCollection().save(new SagaEntry(saga, new JavaSagaSerializer()).asDBObject());
        mongoTemplate.associationsCollection().save(new AssociationValueEntry(identifier, new AssociationValue("key", "value")).asDBObject());

        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        loaded.removeAssociationValue("key", "value");
        Set<MyTestSaga> found = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(0, found.size());
    }

    @DirtiesContext
    @Test
    public void testSaveSaga() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        mongoTemplate.sagaCollection().save(new SagaEntry(saga, new JavaSagaSerializer()).asDBObject());
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        loaded.counter = 1;
        repository.commit(loaded);

        SagaEntry entry = new SagaEntry(mongoTemplate.sagaCollection().findOne(SagaEntry.queryByIdentifier(identifier)));
        MyTestSaga actualSaga = (MyTestSaga) entry.getSaga(new JavaSagaSerializer());
        assertNotSame(loaded, actualSaga);
        assertEquals(1, actualSaga.counter);
    }

    @DirtiesContext
    @Test
    public void testEndSaga() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        mongoTemplate.sagaCollection().save(new SagaEntry(saga, new JavaSagaSerializer()).asDBObject());
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        loaded.end();
        repository.commit(loaded);

        assertNull(mongoTemplate.sagaCollection().findOne(SagaEntry.queryByIdentifier(identifier)));
    }

    @DirtiesContext
    @Test(expected = SagaStorageException.class)
    public void testStoreAssociationValue_NotSerializable() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", new Object()));
        repository.add(saga);
    }


    public static class MyTestSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -1562911263884220240L;
        private int counter = 0;

        public MyTestSaga(String identifier) {
            super(identifier);
        }

        public void registerAssociationValue(AssociationValue associationValue) {
            associateWith(associationValue);
        }

        public void removeAssociationValue(String key, String value) {
            removeAssociationWith(key, value);
        }

        @Override
        public void end() {
            super.end();
        }
    }

    public static class MyOtherTestSaga extends AbstractAnnotatedSaga {
        private static final long serialVersionUID = -1562911263884220240L;
        private int counter = 0;

        public MyOtherTestSaga(String identifier) {
            super(identifier);
        }

        public void registerAssociationValue(AssociationValue associationValue) {
            associateWith(associationValue);
        }

        public void removeAssociationValue(String key, String value) {
            removeAssociationWith(key, value);
        }
    }

    private class InexistentSaga implements Saga {
        @Override
        public String getSagaIdentifier() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public AssociationValues getAssociationValues() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public void handle(Event event) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public boolean isActive() {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }

}
