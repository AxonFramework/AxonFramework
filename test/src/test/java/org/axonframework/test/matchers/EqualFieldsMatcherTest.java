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

package org.axonframework.test.matchers;

import org.axonframework.test.aggregate.MyEvent;
import org.axonframework.test.aggregate.MyOtherEvent;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class EqualFieldsMatcherTest {

    private EqualFieldsMatcher<MyEvent> testSubject;
    private MyEvent expectedEvent;
    private String aggregateId = "AggregateId";

    @BeforeEach
    void setUp() {
        expectedEvent = new MyEvent(aggregateId, 1);
        testSubject = Matchers.equalTo(expectedEvent);
    }

    @Test
    void testMatches_SameInstance() {
        assertTrue(testSubject.matches(expectedEvent));
    }

    @Test
    void testMatches_EqualInstance() {
        assertTrue(testSubject.matches(new MyEvent(aggregateId, 1)));
    }

    @Test
    void testMatches_WrongEventType() {
        assertFalse(testSubject.matches(new MyOtherEvent()));
    }

    @Test
    void testMatches_WrongFieldValue() {
        assertFalse(testSubject.matches(new MyEvent(aggregateId, 2)));
        assertEquals("someValue", testSubject.getFailedField().getName());
    }

    @Test
    void testMatches_WrongFieldValueInIgnoredField() {
        testSubject = Matchers.equalTo(expectedEvent, field -> !field.getName().equals("someValue"));
        assertTrue(testSubject.matches(new MyEvent(aggregateId, 2)));
    }

    @Test
    void testMatches_WrongFieldValueInArray() {
        assertFalse(testSubject.matches(new MyEvent(aggregateId, 1, new byte[]{1, 2})));
        assertEquals("someBytes", testSubject.getFailedField().getName());
    }

    @Test
    void testDescription_AfterSuccess() {
        testSubject.matches(expectedEvent);
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertEquals("org.axonframework.test.aggregate.MyEvent", description.toString());
    }

    @Test
    void testDescription_AfterMatchWithWrongType() {
        testSubject.matches(new MyOtherEvent());
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertEquals("org.axonframework.test.aggregate.MyEvent", description.toString());
    }

    @Test
    void testDescription_AfterMatchWithWrongFieldValue() {
        testSubject.matches(new MyEvent(aggregateId, 2));
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertEquals("org.axonframework.test.aggregate.MyEvent (failed on field 'someValue')", description.toString());
    }
}
