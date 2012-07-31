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
