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
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.mongo.eventsourcing.eventstore.DefaultMongoTemplate;
import org.axonframework.mongo.eventsourcing.eventstore.MongoEventStorageEngine;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;

/**
 * @author Jettro Coenradie
 */
public class MongoEventStoreBenchMark extends AbstractEventStoreBenchmark {

    private static final IdentifierFactory IDENTIFIER_FACTORY = IdentifierFactory.getInstance();
    private EventStore eventStore;

    private MongoClient mongoDb;

    public static void main(String[] args) throws Exception {
        AbstractEventStoreBenchmark benchmark = prepareBenchMark("META-INF/spring/benchmark-mongo-context.xml");
        benchmark.startBenchMark();
    }

    public MongoEventStoreBenchMark(MongoClient mongoDb, MongoEventStorageEngine mongoEventStorageEngine) {
        this.mongoDb = mongoDb;
        this.eventStore = new EmbeddedEventStore(mongoEventStorageEngine);
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new MongoBenchmark();
    }

    @Override
    protected void prepareEventStore() {
        DefaultMongoTemplate mongoTemplate = new DefaultMongoTemplate(mongoDb);
        mongoTemplate.eventCollection().drop();
        mongoTemplate.snapshotCollection().drop();
    }

    private class MongoBenchmark implements Runnable {

        @Override
        public void run() {
            final String aggregateId = IDENTIFIER_FACTORY.generateIdentifier();
            int eventSequence = 0;
            for (int t = 0; t < getTransactionCount(); t++) {
                eventSequence = saveAndLoadLargeNumberOfEvents(aggregateId, eventStore, eventSequence);
            }
        }
    }
}
