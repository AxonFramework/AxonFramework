/*
 * Copyright (c) 2010-2017. Axon Framework
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
import com.mongodb.WriteConcern;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.mongo.DefaultMongoTemplate;
import org.axonframework.mongo.MongoTemplate;
import org.axonframework.mongo.serialization.DBObjectXStreamSerializer;
import org.axonframework.mongo.utils.MongoLauncher;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.junit.*;
import org.junit.runner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;

import static org.mockito.Mockito.*;


/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MongoEventStorageEngineTest_DBObjectSerialization.TestContext.class)
public class MongoEventStorageEngineTest_DBObjectSerialization extends AbstractMongoEventStorageEngineTest {

    private static final Logger logger =
            LoggerFactory.getLogger(MongoEventStorageEngineTest_DBObjectSerialization.class);

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

    @Override
    protected AbstractEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        Serializer serializer = context.getBean(Serializer.class);
        return MongoEventStorageEngine.builder()
                                      .snapshotSerializer(serializer)
                                      .upcasterChain(upcasterChain)
                                      .eventSerializer(serializer)
                                      .mongoTemplate(mongoTemplate)
                                      .build();
    }

    @Override
    protected AbstractEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        Serializer serializer = context.getBean(Serializer.class);
        return MongoEventStorageEngine.builder()
                                      .snapshotSerializer(serializer)
                                      .persistenceExceptionResolver(persistenceExceptionResolver)
                                      .eventSerializer(serializer)
                                      .mongoTemplate(mongoTemplate)
                                      .build();
    }

    @Configuration
    public static class TestContext {

        @Bean
        public MongoEventStorageEngine mongoEventStorageEngine(Serializer serializer, MongoTemplate mongoTemplate) {
            return MongoEventStorageEngine.builder()
                                          .snapshotSerializer(serializer)
                                          .eventSerializer(serializer)
                                          .mongoTemplate(mongoTemplate)
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
            mongoFactory.setWriteConcern(WriteConcern.JOURNALED);
            return mongoFactory;
        }

        @Bean
        public MongoOptionsFactory mongoOptionsFactory() {
            MongoOptionsFactory mongoOptionsFactory = new MongoOptionsFactory();
            mongoOptionsFactory.setConnectionsPerHost(100);
            return mongoOptionsFactory;
        }

        @Bean
        public PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
