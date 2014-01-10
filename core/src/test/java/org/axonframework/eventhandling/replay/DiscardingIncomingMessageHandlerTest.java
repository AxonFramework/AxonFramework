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

package org.axonframework.eventhandling.replay;

import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.Cluster;
import org.junit.*;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DiscardingIncomingMessageHandlerTest {

    private DiscardingIncomingMessageHandler testSubject;
    private Cluster mockCluster;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new DiscardingIncomingMessageHandler();
        mockCluster = mock(Cluster.class);
        this.testSubject.prepareForReplay(mockCluster);
    }

    @Test
    public void testNoMessagesPlacedInBacklog() throws Exception {
        // Event non-domain event messages are discarded
        testSubject.onIncomingMessages(mockCluster, GenericEventMessage.asEventMessage("Test"));
        testSubject.processBacklog(mockCluster);
        testSubject.releaseMessage(mockCluster, new GenericDomainEventMessage<String>("aggregate", 0, "Testing"));

        // the cluster name may be asked for logging
        verify(mockCluster, atMost(1)).getName();
        verifyNoMoreInteractions(mockCluster);
    }

    @Test
    public void testNoAttemptToLogOnEmptyMessages() throws Exception {
        // Event non-domain event messages are discarded
        testSubject.onIncomingMessages(mockCluster);
        testSubject.processBacklog(mockCluster);
        testSubject.releaseMessage(mockCluster, new GenericDomainEventMessage<String>("aggregate", 0, "Testing"));

        // the cluster name may be asked for logging
        verifyZeroInteractions(mockCluster);
    }
}
