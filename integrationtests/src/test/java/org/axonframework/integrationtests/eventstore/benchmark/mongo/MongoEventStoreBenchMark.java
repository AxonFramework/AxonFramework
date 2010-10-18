/*
 * Copyright (c) 2010. Gridshore
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

import com.mongodb.Mongo;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.eventstore.mongo.AxonMongoWrapper;
import org.axonframework.eventstore.mongo.MongoEventStore;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;

/**
 * @author Jettro Coenradie
 */
public class MongoEventStoreBenchMark extends AbstractEventStoreBenchmark {

    private MongoEventStore mongoEventStore;

    private Mongo mongoDb;

    public static void main(String[] args) throws Exception {
        AbstractEventStoreBenchmark benchmark = prepareBenchMark("META-INF/spring/benchmark-mongo-context.xml");
        benchmark.startBenchMark();
    }

    public MongoEventStoreBenchMark(Mongo mongoDb, MongoEventStore mongoEventStore) {
        this.mongoDb = mongoDb;
        this.mongoEventStore = mongoEventStore;
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new MongoBenchmark();
    }

    @Override
    protected void prepareEventStore() {
        AxonMongoWrapper axonMongoWrapper = new AxonMongoWrapper(mongoDb);
        axonMongoWrapper.database().dropDatabase();
    }

    private class MongoBenchmark implements Runnable {

        @Override
        public void run() {
            final AggregateIdentifier aggregateId = AggregateIdentifierFactory.randomIdentifier();
            int eventSequence = 0;
            for (int t = 0; t < getTransactionCount(); t++) {
                eventSequence = saveAndLoadLargeNumberOfEvents(aggregateId, mongoEventStore, eventSequence + 1);
            }
            System.out.println("Sequence : " + eventSequence);
        }
    }

}
