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

package org.axonframework.axonserver.connector.processor.grpc;

import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.eventhandling.Segment.ROOT_SEGMENT;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by Sara Pellegrini on 01/08/2018.
 * sara.pellegrini@gmail.com
 */
@RunWith(MockitoJUnitRunner.class)
public class TrackingEventProcessorInfoMessageTest {

    @Mock
    private TrackingEventProcessor trackingEventProcessor;

    private Map<Integer, EventTrackerStatus> processingStatus = new HashMap<Integer, EventTrackerStatus>(){{
        this.put(0, new FakeEventTrackerStatus(ROOT_SEGMENT, true, false, new GlobalSequenceTrackingToken(100)));
    }};

    @Test
    public void instruction() {
        when(trackingEventProcessor.processingStatus()).thenReturn(processingStatus);
        when(trackingEventProcessor.getName()).thenReturn("ProcessorName");
        when(trackingEventProcessor.activeProcessorThreads()).thenReturn(3);
        when(trackingEventProcessor.availableProcessorThreads()).thenReturn(5);
        when(trackingEventProcessor.isRunning()).thenReturn(false);
        when(trackingEventProcessor.isError()).thenReturn(true);
        TrackingEventProcessorInfoMessage testSubject = new TrackingEventProcessorInfoMessage(trackingEventProcessor);
        EventProcessorInfo eventProcessorInfo = testSubject.instruction().getEventProcessorInfo();
        assertEquals("ProcessorName", eventProcessorInfo.getProcessorName());
        assertEquals(3, eventProcessorInfo.getActiveThreads());
        assertEquals(1, eventProcessorInfo.getEventTrackersInfoCount());
        assertFalse(eventProcessorInfo.getRunning());
        assertTrue(eventProcessorInfo.getError());
        assertTrue(eventProcessorInfo.getAvailableThreads()>0);
        EventProcessorInfo.EventTrackerInfo eventTrackersInfo = eventProcessorInfo.getEventTrackersInfo(0);
        assertEquals(0,eventTrackersInfo.getSegmentId());
        assertEquals(1, eventTrackersInfo.getOnePartOf());
        assertTrue(eventTrackersInfo.getCaughtUp());
        assertFalse(eventTrackersInfo.getReplaying());
    }
}