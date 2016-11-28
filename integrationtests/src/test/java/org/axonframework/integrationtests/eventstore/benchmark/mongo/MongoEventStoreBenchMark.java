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

package org.axonframework.integrationtests.eventstore.benchmark.mongo;

import com.mongodb.MongoClient;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;
import org.axonframework.mongo.eventsourcing.eventstore.DefaultMongoTemplate;
import org.axonframework.mongo.eventsourcing.eventstore.MongoEventStorageEngine;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Jettro Coenradie
 */
public class MongoEventStoreBenchMark extends AbstractEventStoreBenchmark {

    private final MongoClient mongoDb;

    public static void main(String[] args) throws Exception {
        ApplicationContext context = new ClassPathXmlApplicationContext("META-INF/spring/benchmark-mongo-context.xml");
        AbstractEventStoreBenchmark benchmark = context.getBean(AbstractEventStoreBenchmark.class);
        benchmark.start();
    }

    public MongoEventStoreBenchMark(MongoClient mongoDb, MongoEventStorageEngine mongoEventStorageEngine) {
        super(mongoEventStorageEngine);
        this.mongoDb = mongoDb;
    }

    @Override
    protected void prepareForBenchmark() {
        DefaultMongoTemplate mongoTemplate = new DefaultMongoTemplate(mongoDb);
        mongoTemplate.eventCollection().drop();
        mongoTemplate.snapshotCollection().drop();
        getStorageEngine().ensureIndexes();
        super.prepareForBenchmark();
    }

    @Override
    protected MongoEventStorageEngine getStorageEngine() {
        return (MongoEventStorageEngine) super.getStorageEngine();
    }
}
