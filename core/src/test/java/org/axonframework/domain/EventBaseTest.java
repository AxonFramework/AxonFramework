/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.domain;

import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDateTime;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class EventBaseTest {

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void testTimeStamp() {
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis());
        Event event1 = new SimpleEvent();

        assertEquals(new LocalDateTime(), event1.getTimestamp());
    }

    @Test
    public void testEquality() {
        Event event1 = new SimpleEvent();
        assertFalse(event1.equals(new Object()));
        assertTrue(event1.equals(event1));
        assertFalse(event1.equals(null));
    }

    private static class SimpleEvent extends EventBase {

    }

}
