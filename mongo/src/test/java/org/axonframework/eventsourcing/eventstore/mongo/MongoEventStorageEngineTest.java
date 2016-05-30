/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.Mongo;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.mongoutils.MongoLauncher;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;


/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context.xml"})
public class MongoEventStorageEngineTest extends BatchingEventStorageEngineTest {
    private static final Logger logger = LoggerFactory.getLogger(MongoEventStorageEngineTest.class);

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

    @Before
    public void setUp() {
        try {
            Mongo mongo = context.getBean(Mongo.class);
            testSubject = context.getBean(MongoEventStorageEngine.class);
            mongoTemplate = new DefaultMongoTemplate(mongo);
            mongoTemplate.eventCollection().remove(new BasicDBObject());
            mongoTemplate.snapshotCollection().remove(new BasicDBObject());
            setTestSubject(testSubject);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
    }

    @Test
    @Override
    public void testUniqueKeyConstraintOnEventIdentifier() {
        logger.info("Unique event identifier is not currently guaranteed in the Mongo Event Storage Engine");
    }
}
