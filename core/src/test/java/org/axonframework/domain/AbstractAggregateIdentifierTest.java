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

import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AbstractAggregateIdentifierTest {

    private AbstractAggregateIdentifier testSubject;

    @Before
    public void setUp() {
        testSubject = new StringAggregateIdentifier("bcde");
    }

    @Test
    public void testToString() throws Exception {
        assertEquals("bcde", testSubject.toString());
    }

    @Test
    public void testHashCode() throws Exception {
        assertEquals("bcde".hashCode(), testSubject.hashCode());
    }

    @Test
    public void testCompareTo() throws Exception {
        assertEquals(0, testSubject.compareTo(new StringAggregateIdentifier("bcde")));
        assertTrue(testSubject.compareTo(new StringAggregateIdentifier("cdef")) < 0);
        assertTrue(testSubject.compareTo(new StringAggregateIdentifier("abcd")) > 0);
    }

    @Test
    public void testEquals() throws Exception {
        assertTrue(testSubject.equals(new StringAggregateIdentifier("bcde")));
        assertTrue(testSubject.equals(testSubject));
        assertFalse(testSubject.equals(new StringAggregateIdentifier("abc")));

        AggregateIdentifier mockIdentifier = mock(AggregateIdentifier.class);
        when(mockIdentifier.asString()).thenReturn("bcde");
        assertTrue(testSubject.equals(mockIdentifier));
    }
}
