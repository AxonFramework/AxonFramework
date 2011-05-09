/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.test.eventscheduler;

import org.axonframework.domain.ApplicationEvent;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class StubEventSchedulerTest {

    private StubEventScheduler testSubject;

    @Before
    public void setUp() {
        testSubject = new StubEventScheduler();
    }

    @Test
    public void testScheduleEvent() {
        testSubject.schedule(new DateTime().plus(Duration.standardDays(1)), new MockEvent(this));
        assertEquals(1, testSubject.getScheduledItems().size());
    }

    private static class MockEvent extends ApplicationEvent {
        private static final long serialVersionUID = 1722699384492136950L;

        public MockEvent(Object source) {
            super(source);
        }
    }
}
