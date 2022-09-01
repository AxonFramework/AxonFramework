/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;
import org.quartz.JobDataMap;

import java.io.ObjectInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyAwareJobDataBinderTest {

    private LegacyAwareJobDataBinder testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new LegacyAwareJobDataBinder();
    }

    @Test
    void readLegacyInstance() {
        JobDataMap legacyJobDataMap = mock(JobDataMap.class);
        when(legacyJobDataMap.get("org.axonframework.domain.EventMessage")).thenAnswer(
                i -> {
                    try (ObjectInputStream objectInputStream = new ObjectInputStream(getClass().getClassLoader().getResourceAsStream("serialized.object"))) {
                        return objectInputStream.readObject();
                    }
                }
        );

        Object event = testSubject.fromJobData(legacyJobDataMap);
        verify(legacyJobDataMap).get("org.axonframework.domain.EventMessage");
        assertTrue(event instanceof EventMessage);
        EventMessage<?> eventMessage = (EventMessage<?>) event;
        assertEquals("this is the payload", eventMessage.getPayload());
        assertEquals("value", eventMessage.getMetaData().get("key"));
        assertEquals(1, eventMessage.getMetaData().size());
    }

    @Test
    void readRecentInstance() {
        JobDataMap legacyJobDataMap = mock(JobDataMap.class);
        when(legacyJobDataMap.get(EventMessage.class.getName())).thenReturn(new GenericEventMessage<>("new"));

             Object event = testSubject.fromJobData(legacyJobDataMap);
        verify(legacyJobDataMap, never()).get("org.axonframework.domain.EventMessage");
        assertTrue(event instanceof EventMessage);
        EventMessage<?> eventMessage = (EventMessage<?>) event;
        assertEquals("new", eventMessage.getPayload());
        assertEquals(0, eventMessage.getMetaData().size());
    }
}
