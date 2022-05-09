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

package org.axonframework.test.matchers;

import org.axonframework.test.aggregate.MyEvent;
import org.axonframework.test.aggregate.MyOtherEvent;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EqualFieldsMatcher}.
 *
 * @author Allard Buijze
 */
class EqualFieldsMatcherTest {

    private EqualFieldsMatcher<MyEvent> testSubject;
    private MyEvent expectedEvent;
    private final String aggregateId = "AggregateId";

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

    /**
     * This test is introduced to validate whether the matcher doesn't delve into the boxed primitives, since reflective
     * access is restricted for e.g. JDK 17.
     */
    @Test
    void testMatchesReturnsTrueForPrimitives() {
        EqualFieldsMatcher<Boolean> booleanMatcher = Matchers.equalTo(false);
        assertTrue(booleanMatcher.matches(false));
        assertFalse(booleanMatcher.matches(true));
        assertTrue(booleanMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Byte> byteMatcher = Matchers.equalTo((byte) 42);
        assertTrue(byteMatcher.matches((byte) 42));
        assertFalse(byteMatcher.matches((byte) 24));
        assertTrue(byteMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Character> charMatcher = Matchers.equalTo('a');
        assertTrue(charMatcher.matches('a'));
        assertFalse(charMatcher.matches('z'));
        assertTrue(charMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Short> shortMatcher = Matchers.equalTo((short) 42);
        assertTrue(shortMatcher.matches((short) 42));
        assertFalse(shortMatcher.matches((short) 24));
        assertTrue(shortMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Integer> integerMatcher = Matchers.equalTo(42);
        assertTrue(integerMatcher.matches(42));
        assertFalse(integerMatcher.matches(24));
        assertTrue(integerMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Float> floatMatcher = Matchers.equalTo(42F);
        assertTrue(floatMatcher.matches(42F));
        assertFalse(floatMatcher.matches(24F));
        assertTrue(floatMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Double> doubleMatcher = Matchers.equalTo(42.42);
        assertTrue(doubleMatcher.matches(42.42));
        assertFalse(doubleMatcher.matches(24.24));
        assertTrue(doubleMatcher.isFailedPrimitive());

        EqualFieldsMatcher<Long> longMatcher = Matchers.equalTo(42L);
        assertTrue(longMatcher.matches(42L));
        assertFalse(longMatcher.matches(24L));
        assertTrue(longMatcher.isFailedPrimitive());

        EqualFieldsMatcher<String> stringMatcher = Matchers.equalTo("foo");
        assertTrue(stringMatcher.matches("foo"));
        assertFalse(stringMatcher.matches("bar"));
        assertTrue(stringMatcher.isFailedPrimitive());
    }
}
