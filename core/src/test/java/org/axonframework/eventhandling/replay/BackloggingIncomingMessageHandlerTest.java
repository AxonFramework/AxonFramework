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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.eventhandling.Cluster;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class BackloggingIncomingMessageHandlerTest {

    private BackloggingIncomingMessageHandler testSubject;

    private static final DateTime START_TIME = new DateTime();
    private static final DateTime MINUTE_BEFORE_START = START_TIME.minus(Duration.standardMinutes(1));
    private static final DateTime SECOND_BEFORE_START = START_TIME.minus(Duration.standardSeconds(1));
    private static final DateTime SECOND_AFTER_START = START_TIME.plus(Duration.standardSeconds(1));
    private static final DateTime MINUTE_AFTER_START = START_TIME.plus(Duration.standardMinutes(1));
    private Cluster mockCluster;
    private Map<DateTime, DomainEventMessage> messages = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(START_TIME.getMillis());
        this.testSubject = new BackloggingIncomingMessageHandler();
        this.mockCluster = mock(Cluster.class);
        messages.put(MINUTE_BEFORE_START, newDomainEventMessage("id1", MINUTE_BEFORE_START));
        messages.put(SECOND_BEFORE_START, newDomainEventMessage("id2", SECOND_BEFORE_START));
        messages.put(START_TIME, newDomainEventMessage("id3", START_TIME));
        messages.put(SECOND_AFTER_START, newDomainEventMessage("id4", SECOND_AFTER_START));
        messages.put(MINUTE_AFTER_START, newDomainEventMessage("id5", MINUTE_AFTER_START));

        this.testSubject.prepareForReplay(mockCluster);
    }

    @After
    public void tearDown() throws Exception {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void testEventBackloggedForProcessing() throws Exception {
        // this event should be ignored, since it occurred before the threshold
        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(START_TIME));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_AFTER_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_AFTER_START));
        verifyZeroInteractions(mockCluster);

        testSubject.processBacklog(mockCluster);

        verify(mockCluster, times(4)).publish(isA(DomainEventMessage.class));
    }

    @Test
    public void testReleasedEventsRemovedFromBacklog_ReleasedAfterIncoming() throws Exception {
        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(START_TIME));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_AFTER_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_AFTER_START));

        testSubject.releaseMessage(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.releaseMessage(mockCluster, messages.get(START_TIME));
        testSubject.releaseMessage(mockCluster, messages.get(SECOND_AFTER_START));
        verifyZeroInteractions(mockCluster);

        testSubject.processBacklog(mockCluster);

        verify(mockCluster).publish(messages.get(MINUTE_AFTER_START));
        verifyNoMoreInteractions(mockCluster);
    }
    @Test
    public void testReleasedEventsRemovedFromBacklog_ReleasedBeforeIncoming() throws Exception {
        testSubject.releaseMessage(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.releaseMessage(mockCluster, messages.get(START_TIME));
        testSubject.releaseMessage(mockCluster, messages.get(SECOND_AFTER_START));

        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(START_TIME));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_AFTER_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_AFTER_START));
        verifyZeroInteractions(mockCluster);

        testSubject.processBacklog(mockCluster);

        verify(mockCluster).publish(messages.get(MINUTE_AFTER_START));
        verifyNoMoreInteractions(mockCluster);
    }

    @Test
    public void testEventsPublishedImmediatelyAfterReplayFinished() {
        testSubject.releaseMessage(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.releaseMessage(mockCluster, messages.get(START_TIME));
        testSubject.releaseMessage(mockCluster, messages.get(SECOND_AFTER_START));

        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(START_TIME));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_AFTER_START));
        testSubject.processBacklog(mockCluster);
        verifyZeroInteractions(mockCluster);
        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_AFTER_START));


        verify(mockCluster).publish(messages.get(MINUTE_AFTER_START));
        verifyNoMoreInteractions(mockCluster);

    }

    @Test
    public void testEventMessagesArePublishedWhenLaterDomainEventMessageIsReleased() {
        EventMessage intermediateEventMessage = newEventMessage("idBla", START_TIME);
        testSubject.onIncomingMessages(mockCluster, messages.get(MINUTE_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, messages.get(SECOND_BEFORE_START));
        testSubject.onIncomingMessages(mockCluster, intermediateEventMessage);
        testSubject.onIncomingMessages(mockCluster, messages.get(START_TIME));
        testSubject.releaseMessage(mockCluster, messages.get(MINUTE_BEFORE_START));
        testSubject.releaseMessage(mockCluster, messages.get(SECOND_BEFORE_START));
        verifyZeroInteractions(mockCluster);

        testSubject.releaseMessage(mockCluster, messages.get(START_TIME));
        verify(mockCluster).publish(intermediateEventMessage);

        testSubject.releaseMessage(mockCluster, messages.get(SECOND_AFTER_START));
        verifyNoMoreInteractions(mockCluster);
    }

    private DomainEventMessage<String> newDomainEventMessage(String identifier, DateTime timestamp) {
        return new GenericDomainEventMessage<>(identifier, timestamp, "aggregate", 0, "payload@" + timestamp.toString(),
                                                     MetaData.emptyInstance());
    }

    private EventMessage<String> newEventMessage(String identifier, DateTime timestamp) {
        return new GenericEventMessage<>(identifier, timestamp, "payload@" + timestamp.toString(),
                                                     MetaData.emptyInstance());
    }
}
