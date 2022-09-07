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

package org.axonframework.test.eventscheduler;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class StubEventSchedulerTest {

    private StubEventScheduler testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new StubEventScheduler();
    }

    @Test
    void scheduleEvent() {
        testSubject.schedule(Instant.now().plus(Duration.ofDays(1)), event(new MockEvent()));
        assertEquals(1, testSubject.getScheduledItems().size());
    }

    @Test
    void eventContainsTimestampOfScheduledTime() {
        Instant triggerTime = Instant.now().plusSeconds(60);
        testSubject.schedule(triggerTime, "gone");
        List<EventMessage<?>> triggered = new ArrayList<>();
        testSubject.advanceTimeBy(Duration.ofMinutes(75), triggered::add);

        assertEquals(1, triggered.size());
        assertEquals(triggerTime, triggered.get(0).getTimestamp());
    }

    @Test
    void initializeAtDateTimeAfterSchedulingEvent() {
        testSubject.schedule(Instant.now().plus(Duration.ofDays(1)), event(new MockEvent()));

        assertThrows(IllegalStateException.class, () ->
                        testSubject.initializeAt(Instant.now().minus(10, ChronoUnit.MINUTES)));
    }

    private EventMessage<MockEvent> event(MockEvent mockEvent) {
        return new GenericEventMessage<>(mockEvent);
    }

    private static class MockEvent {

    }
}
