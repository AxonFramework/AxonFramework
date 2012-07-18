package org.axonframework.eventstore.cassandra;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class CassandraEventStoreTest {

    @Test
    public void testStoreAndReadEvents() {
        EmbeddedCassandra.start();
        CassandraEventStore cassandraEventStore = new CassandraEventStore(new XStreamSerializer(),
                                                                          EmbeddedCassandra.CLUSTER_NAME,
                                                                          EmbeddedCassandra.KEYSPACE_NAME,
                                                                          EmbeddedCassandra.CF_NAME,
                                                                          EmbeddedCassandra.HOSTS);
        final int batch_count = 100;
        final int batch_size = 10;
        int event_count = 0;
        for (int counter = 0; counter < batch_count; counter++) {
            List<GenericDomainEventMessage> messages = new ArrayList<GenericDomainEventMessage>();
            String id1 = UUID.randomUUID().toString();
            for (int t = 0; t < batch_size; t++) {
                messages.add(new GenericDomainEventMessage<String>(id1,
                                                                   t,
                                                                   "Payload " + t + " for " + id1
                                                                           + " which is just to make it longer"));
            }
            cassandraEventStore.appendEvents("test", new SimpleDomainEventStream(messages));

            DomainEventStream eventStream = cassandraEventStore.readEvents("test", id1);
            while (eventStream.hasNext()) {
                DomainEventMessage<String> eventMessage = eventStream.next();
                assertNotNull(eventMessage);
                assertEquals(String.class, eventMessage.getPayloadType());
                assertTrue(eventMessage.getPayload().startsWith("Payload"));
                    event_count++;
            }
        }
        assertEquals(batch_count * batch_size, event_count);
    }
}
