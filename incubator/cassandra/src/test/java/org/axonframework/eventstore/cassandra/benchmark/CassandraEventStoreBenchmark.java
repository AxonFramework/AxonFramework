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

package org.axonframework.eventstore.cassandra.benchmark;

import org.axonframework.domain.IdentifierFactory;
import org.axonframework.eventstore.cassandra.CassandraEventStore;
import org.axonframework.eventstore.cassandra.EmbeddedCassandra;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;
import org.axonframework.serializer.xml.XStreamSerializer;

/**
 * @author Allard Buijze
 */
public class CassandraEventStoreBenchmark extends AbstractEventStoreBenchmark {

    private static final IdentifierFactory IDENTIFIER_FACTORY = IdentifierFactory.getInstance();

    private CassandraEventStore eventStore;

    public static void main(String[] args) throws InterruptedException {
        AbstractEventStoreBenchmark benchmark = new CassandraEventStoreBenchmark();
        benchmark.startBenchMark();
        System.exit(0);
    }

    @Override
    protected void prepareEventStore() {
        EmbeddedCassandra.start();
        eventStore = new CassandraEventStore(new XStreamSerializer(),
                                             EmbeddedCassandra.CLUSTER_NAME,
                                             EmbeddedCassandra.KEYSPACE_NAME,
                                             EmbeddedCassandra.CF_NAME,
                                             EmbeddedCassandra.HOSTS);
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new CassandraBenchmark();
    }

    private class CassandraBenchmark implements Runnable {

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
