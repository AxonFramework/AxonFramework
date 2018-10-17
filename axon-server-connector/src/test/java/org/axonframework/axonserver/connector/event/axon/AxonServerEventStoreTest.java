/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.event.StubServer;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class AxonServerEventStoreTest {

    private StubServer server;
    private AxonServerEventStore testSubject;

    @Before
    public void setUp() throws Exception {
        server = new StubServer(6123);
        server.start();
        AxonServerConfiguration config = AxonServerConfiguration.builder()
                                                                .servers("localhost:6123")
                                                                .componentName("JUNIT")
                                                                .flowControl(2, 1, 1)
                                                                .build();
        testSubject = AxonServerEventStore.builder()
                                          .configuration(config)
                                          .platformConnectionManager(new AxonServerConnectionManager(config))
                                          .build();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void testPublishAndConsumeEvents() throws Exception {
        UnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        testSubject.publish(GenericEventMessage.asEventMessage("Test1"),
                            GenericEventMessage.asEventMessage("Test2"),
                            GenericEventMessage.asEventMessage("Test3"));
        uow.commit();

        TrackingEventStream stream = testSubject.openStream(null);

        List<String> received = new ArrayList<>();
        while (stream.hasNextAvailable(100, TimeUnit.MILLISECONDS)) {
            received.add(stream.nextAvailable().getPayload().toString());
        }
        stream.close();

        assertEquals(Arrays.asList("Test1", "Test2", "Test3"), received);
    }

    @Test(expected = EventStoreException.class)
    public void testLastSequenceNumberFor() {
        testSubject.lastSequenceNumberFor("Agg1");
    }
}
