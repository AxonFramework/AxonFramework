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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.junit.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 *
 */
public class ClusteringEventBusTest {

    private ClusteringEventBus eventBus;

    @Before
    public void setUp() {
        eventBus = new ClusteringEventBus();
    }

    @Test
    public void testEventIsPublishedToAllClustersWithDefaultConfiguration() {
        RecordingClusteredEventListener listener1 = new RecordingClusteredEventListener("cluster1");
        RecordingClusteredEventListener listener2 = new RecordingClusteredEventListener("cluster2");
        eventBus.subscribe(listener1);
        eventBus.subscribe(listener2);

        eventBus.publish(new GenericEventMessage<Object>(new Object()));

        assertEquals(1, listener1.getReceivedEvents().size());
        assertEquals(1, listener2.getReceivedEvents().size());
    }

    @Test
    public void testEventSentToTerminal() {
        EventBusTerminal mockTerminal = mock(EventBusTerminal.class);
        eventBus = new ClusteringEventBus(mockTerminal);
        EventListener mockEventListener = mock(EventListener.class);

        eventBus.subscribe(mockEventListener);

        eventBus.publish(new GenericEventMessage<Object>(new Object()));

        verify(mockTerminal).publish(isA(EventMessage.class));
        verify(mockEventListener, never()).handle(Matchers.<EventMessage>any());
    }

    private class RecordingClusteredEventListener implements EventListener {

        private final List<EventMessage> receivedEvents = new ArrayList<EventMessage>();
        private final String clusterName;

        public RecordingClusteredEventListener(String clusterName) {
            this.clusterName = clusterName;
        }

        @Override
        public void handle(EventMessage event) {
            receivedEvents.add(event);
        }

        public List<EventMessage> getReceivedEvents() {
            return receivedEvents;
        }
    }
}
