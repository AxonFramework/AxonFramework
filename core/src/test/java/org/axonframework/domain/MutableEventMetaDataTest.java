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

import org.joda.time.LocalDateTime;
import org.junit.*;

import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class MutableEventMetaDataTest {

    private MutableEventMetaData testSubject;
    private UUID eventIdentifier;
    private LocalDateTime timestamp;

    @Before
    public void setUp() {
        eventIdentifier = UUID.randomUUID();
        timestamp = new LocalDateTime();
        testSubject = new MutableEventMetaData(timestamp, eventIdentifier);
    }

    @Test
    public void testGetStandardProperties() {
        assertSame(eventIdentifier, testSubject.getEventIdentifier());
        assertSame(eventIdentifier, testSubject.get("_identifier"));
        assertSame(timestamp, testSubject.getTimestamp());
        assertSame(timestamp, testSubject.get("_timestamp"));
    }

    @Test
    public void testGetAndSetProperties() {
        testSubject.put("myKey", "myValue");
        assertEquals("myValue", testSubject.get("myKey"));
        testSubject.put("myKey", "myNewValue");
        assertEquals("myNewValue", testSubject.get("myKey"));

        assertNull(testSubject.get("doesNotExist"));
    }

    @Test
    public void testGetKeySet() {
        testSubject.put("myKey", "myValue");
        Set<String> actualResult = testSubject.keySet();
        assertEquals(3, actualResult.size());
        assertTrue(actualResult.contains("myKey"));
    }

}
