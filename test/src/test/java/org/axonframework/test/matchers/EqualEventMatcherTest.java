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

package org.axonframework.test.matchers;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.MutableEventMetaData;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.test.MyEvent;
import org.axonframework.test.MyOtherEvent;
import org.hamcrest.StringDescription;
import org.junit.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class EqualEventMatcherTest {
    private EqualEventMatcher<MyEvent> testSubject;
    private MyEvent expectedEvent;

    @Before
    public void setUp() {
        expectedEvent = new MyEvent(1);
        testSubject = Matchers.equalTo(expectedEvent);
    }

    @Test
    public void testMatches_SameInstance() {
        assertTrue(testSubject.matches(expectedEvent));
    }

    @Test
    public void testMatches_EqualInstance() {
        assertTrue(testSubject.matches(new MyEvent(1)));
    }

    @Test
    public void testMatches_MetaDataDifference() throws Exception {
        MyEvent actual = new MyEvent(1);
        setAggregateIdentifier(actual, new UUIDAggregateIdentifier());
        ((MutableEventMetaData) actual.getMetaData()).put("newKey", "newValue");
        assertTrue(testSubject.matches(actual));
    }

    @Test
    public void testMatches_WrongEventType() {
        assertFalse(testSubject.matches(new MyOtherEvent()));
    }

    @Test
    public void testMatches_WrongFieldValue() {
        assertFalse(testSubject.matches(new MyEvent(2)));
        assertEquals("someValue", testSubject.getFailedField().getName());
    }

    @Test
    public void testMatches_WrongFieldValueInArray() {
        assertFalse(testSubject.matches(new MyEvent(1, new byte[]{1, 2})));
        assertEquals("someBytes", testSubject.getFailedField().getName());
    }

    @Test
    public void testDescription_AfterSuccess() {
        testSubject.matches(expectedEvent);
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertEquals("org.axonframework.test.MyEvent", description.toString());
    }

    @Test
    public void testDescription_AfterMatchWithWrongType() {
        testSubject.matches(new MyOtherEvent());
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertEquals("org.axonframework.test.MyEvent", description.toString());
    }

    @Test
    public void testDescription_AfterMatchWithWrongFieldValue() {
        testSubject.matches(new MyEvent(2));
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertEquals("org.axonframework.test.MyEvent (failed on field 'someValue')", description.toString());
    }

    private void setAggregateIdentifier(MyEvent actual, UUIDAggregateIdentifier uuidAggregateIdentifier)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setter = DomainEvent.class.getDeclaredMethod("setAggregateIdentifier", AggregateIdentifier.class);
        setter.setAccessible(true);
        setter.invoke(actual, uuidAggregateIdentifier);
    }
}
