/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.mongo.DefaultMongoTemplate;
import org.axonframework.mongo.MongoTemplate;
import org.axonframework.mongo.eventhandling.saga.repository.MongoSagaStore;
import org.axonframework.mongo.eventsourcing.eventstore.documentpercommit.DocumentPerCommitStorageStrategy;
import org.axonframework.mongo.serialization.DBObjectXStreamSerializer;
import org.axonframework.mongo.utils.MongoLauncher;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.junit.*;
import org.junit.runner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.AGGREGATE;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MongoEventStorageEngineTest_DocPerCommit.TestContext.class)
public class MongoEventStorageEngineTest_DocPerCommit extends AbstractMongoEventStorageEngineTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStorageEngineTest_DocPerCommit.class);

    private static MongodExecutable mongoExe;
    private static MongodProcess mongod;

    private MongoEventStorageEngine testSubject;

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

    @Autowired
    private ApplicationContext context;

    private DefaultMongoTemplate mongoTemplate;

    @SuppressWarnings("Duplicates")
    @Before
    public void setUp() {
        MongoClient mongoClient = null;
        try {
            mongoClient = context.getBean(MongoClient.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate = DefaultMongoTemplate.builder().mongoDatabase(mongoClient).build();
        mongoTemplate.eventCollection().deleteMany(new BasicDBObject());
        mongoTemplate.snapshotCollection().deleteMany(new BasicDBObject());
        mongoTemplate.eventCollection().dropIndexes();
        mongoTemplate.snapshotCollection().dropIndexes();
        testSubject = context.getBean(MongoEventStorageEngine.class);
        setTestSubject(testSubject);
        testSubject.ensureIndexes();
    }

    @Test
    @Override
    public void testUniqueKeyConstraintOnEventIdentifier() {
        logger.info("Unique event identifier is not currently guaranteed in the Mongo Event Storage Engine");
    }

    @Test
    public void testFetchHighestSequenceNumber() {
        testSubject.appendEvents(createEvent(0), createEvent(1));
        testSubject.appendEvents(createEvent(2), createEvent(3));

        assertEquals(3, (long) testSubject.lastSequenceNumberFor(AGGREGATE).get());
        assertFalse(testSubject.lastSequenceNumberFor("notexist").isPresent());
    }

    @Test
    public void testFetchingEventsReturnsResultsWhenMoreThanBatchSizeCommitsAreAvailable() {
        testSubject.appendEvents(createEvent(0), createEvent(1), createEvent(2));
        testSubject.appendEvents(createEvent(3), createEvent(4), createEvent(5));
        testSubject.appendEvents(createEvent(6), createEvent(7), createEvent(8));
        testSubject.appendEvents(createEvent(9), createEvent(10), createEvent(11));
        testSubject.appendEvents(createEvent(12), createEvent(13), createEvent(14));

        List<? extends TrackedEventData<?>> result = testSubject.fetchTrackedEvents(null, 2);
        TrackedEventData<?> last = result.get(result.size() - 1);// we decide to omit the last event
        last.trackingToken();
        List<? extends TrackedEventData<?>> actual = testSubject.fetchTrackedEvents(last.trackingToken(), 2);
        assertFalse("Expected to retrieve some events", actual.isEmpty());
        // we want to make sure we get the first event from the next commit
        assertEquals(result.size(), ((DomainEventData<?>) actual.get(0)).getSequenceNumber());
    }

    @Test
    public void testFetchingEventsReturnsResultsWhenMoreThanBatchSizeCommitsAreAvailable_PartiallyReadingCommit() {
        testSubject.appendEvents(createEvent(0), createEvent(1), createEvent(2));
        testSubject.appendEvents(createEvent(3), createEvent(4), createEvent(5));
        testSubject.appendEvents(createEvent(6), createEvent(7), createEvent(8));
        testSubject.appendEvents(createEvent(9), createEvent(10), createEvent(11));
        testSubject.appendEvents(createEvent(12), createEvent(13), createEvent(14));

        List<? extends TrackedEventData<?>> result = testSubject.fetchTrackedEvents(null, 2);
        TrackedEventData<?> last = result.get(result.size() - 2);// we decide to omit the last event
        last.trackingToken();
        List<? extends TrackedEventData<?>> actual = testSubject.fetchTrackedEvents(last.trackingToken(), 2);
        assertFalse("Expected to retrieve some events", actual.isEmpty());
        // we want to make sure we get the last event from the commit
        assertEquals(result.size() - 1, ((DomainEventData<?>) actual.get(0)).getSequenceNumber());
    }

    @Test(expected = ConcurrencyException.class)
    @Override
    public void testStoreDuplicateEventWithoutExceptionResolver() {
        testSubject.appendEvents(createEvent(0));
        testSubject.appendEvents(createEvent(0));
    }

    @DirtiesContext
    @Test(expected = EventStoreException.class)
    @Override
    public void testStoreDuplicateEventWithExceptionTranslator() {
        testSubject = createEngine((PersistenceExceptionResolver) null);
        testSubject.appendEvents(createEvent(0));
        testSubject.appendEvents(createEvent(0));
    }

    @Override
    protected MongoEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        return MongoEventStorageEngine.builder()
                                      .upcasterChain(upcasterChain)
                                      .mongoTemplate(mongoTemplate)
                                      .storageStrategy(new DocumentPerCommitStorageStrategy())
                                      .build();
    }

    @Override
    protected MongoEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        return MongoEventStorageEngine.builder()
                                      .persistenceExceptionResolver(persistenceExceptionResolver)
                                      .mongoTemplate(mongoTemplate)
                                      .storageStrategy(new DocumentPerCommitStorageStrategy())
                                      .build();
    }

    @Override
    public void testCreateTokenAtExactTime() {
        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.01Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.01Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3), readEvents);
    }

    @Override
    public void testCreateTokenWithUnorderedEvents() {

        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:46.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:50.00Z"));
        DomainEventMessage<String> event4 = createEvent(3, Instant.parse("2007-12-03T10:15:45.00Z"));
        DomainEventMessage<String> event5 = createEvent(4, Instant.parse("2007-12-03T10:15:42.00Z"));

        testSubject.appendEvents(event1, event2, event3, event4, event5);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:45.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3, event4, event5), readEvents);
    }

    @Configuration
    public static class TestContext {

        @Bean
        public MongoEventStorageEngine mongoEventStorageEngine(Serializer serializer, MongoTemplate mongoTemplate) {
            return MongoEventStorageEngine.builder()
                                          .snapshotSerializer(serializer)
                                          .eventSerializer(serializer)
                                          .mongoTemplate(mongoTemplate)
                                          .storageStrategy(new DocumentPerCommitStorageStrategy())
                                          .build();
        }

        @Bean
        public Serializer serializer() {
            return DBObjectXStreamSerializer.builder().build();
        }

        @Bean
        public MongoTemplate mongoTemplate(MongoClient mongoClient) {
            return DefaultMongoTemplate.builder()
                                       .mongoDatabase(mongoClient)
                                       .build();
        }

        @Bean
        public MongoClient mongoClient(MongoFactory mongoFactory) {
            return mongoFactory.createMongo();
        }

        @Bean
        public MongoFactory mongoFactoryBean(MongoOptionsFactory mongoOptionsFactory) {
            MongoFactory mongoFactory = new MongoFactory();
            mongoFactory.setMongoOptions(mongoOptionsFactory.createMongoOptions());
            return mongoFactory;
        }

        @Bean
        public MongoOptionsFactory mongoOptionsFactory() {
            MongoOptionsFactory mongoOptionsFactory = new MongoOptionsFactory();
            mongoOptionsFactory.setConnectionsPerHost(100);
            return mongoOptionsFactory;
        }

        @Bean
        public SpringResourceInjector springResourceInjector() {
            return new SpringResourceInjector();
        }

        @Bean
        public MongoSagaStore mongoSagaStore(MongoTemplate sagaMongoTemplate) {
            return MongoSagaStore.builder().mongoTemplate(sagaMongoTemplate).build();
        }

        @Bean
        public MongoTemplate sagaMongoTemplate(MongoClient mongoClient) {
            return DefaultMongoTemplate.builder().mongoDatabase(mongoClient).build();
        }

        @Bean
        public PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
