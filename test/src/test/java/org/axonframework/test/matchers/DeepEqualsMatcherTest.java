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

import org.axonframework.common.AxonConfigurationException;
import org.hamcrest.Description;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DeepEqualsMatcher}.
 *
 * @author Steven van Beelen
 */
class DeepEqualsMatcherTest {

    @Test
    void testMatchesReturnsFalseForNoneMatchingTypes() {
        Description description = new StringDescription();

        DeepEqualsMatcher<String> testSubject = new DeepEqualsMatcher<>("foo");

        boolean result = testSubject.matches(42);

        assertFalse(result);
        testSubject.describeTo(description);
        assertTrue(description.toString().contains(" does not match with the actual type."));
    }

    @Test
    void testMatchesReturnsFalseForNoneMatchingEquals() {
        Description description = new StringDescription();

        DeepEqualsMatcher<String> testSubject = new DeepEqualsMatcher<>("foo");

        boolean result = testSubject.matches("bar");

        assertFalse(result);
        testSubject.describeTo(description);
        assertTrue(description.toString().contains(" does not equal with the actual instance."));
    }

    @Test
    void testMatchesReturnsFalseForNoneMatchingFields() {
        Description description = new StringDescription();

        DeepEqualsMatcher<ObjectNotOverridingEquals> testSubject =
                new DeepEqualsMatcher<>(new ObjectNotOverridingEquals("foo"));

        boolean result = testSubject.matches(new ObjectNotOverridingEquals("bar"));

        assertFalse(result);
        testSubject.describeTo(description);
        assertTrue(description.toString().contains(" (failed on field '"));
    }

    @Test
    void testMatchesReturnsTrue() {
        assertTrue(new DeepEqualsMatcher<>("foo").matches("foo"));
    }

    @Test
    void testMatchesIsNullSafe() {
        assertFalse(new DeepEqualsMatcher<>("foo").matches(null));
        assertThrows(AxonConfigurationException.class, () -> new DeepEqualsMatcher<>(null).matches("foo"));
    }

    @Test
    void testIgnoredFieldOnEvent(){
        DeepEqualsMatcher<SomeEvent> testSubject = new DeepEqualsMatcher<>(new SomeEvent("someField"), new IgnoreField(SomeEvent.class, "someField"));
        boolean result = testSubject.matches(new SomeEvent("otherField"));
        assertTrue(result);
    }

    private static class ObjectNotOverridingEquals {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String someField;

        private ObjectNotOverridingEquals(String someField) {
            this.someField = someField;
        }
    }
    private static class SomeEvent {
        @SuppressWarnings({"unused"})
        private final String someField;

        private SomeEvent(String someField) {
            this.someField = someField;
        }
    }
}