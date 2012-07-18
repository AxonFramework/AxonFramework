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
