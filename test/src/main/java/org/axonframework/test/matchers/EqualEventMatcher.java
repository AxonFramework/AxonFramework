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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.axonframework.domain.EventBase;
import org.axonframework.test.FixtureExecutionException;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Matcher that will match an Event if all the fields on that Event contain values equal to the same field in the
 * expected event. All fields in the hierarchy are compared, with the exclusion of the aggregate identifier, sequence
 * number and meta data, which are generally not set in the expected events.
 *
 * @param <T> The type of event m
 * @author Allard Buijze
 * @since 1.1
 */
public class EqualEventMatcher<T extends Event> extends BaseMatcher<T> {

    private final T expected;
    private Field failedField;
    private Object failedFieldExpectedValue;
    private Object failedFieldActualValue;

    /**
     * Initializes an EqualEventMatcher that will match an event with equal properties as the given
     * <code>expected</code> event.
     *
     * @param expected The expected event
     */
    public EqualEventMatcher(T expected) {
        this.expected = expected;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public boolean matches(Object item) {
        return expected.getClass().isInstance(item) && matchesSafely((T) item);
    }

    private boolean matchesSafely(Event actualEvent) {
        return expected.getClass().equals(actualEvent.getClass())
                && fieldsMatch(expected.getClass(), expected, actualEvent);
    }

    private boolean fieldsMatch(Class<?> aClass, Event expectedEvent, Event actualEvent) {
        boolean match = true;
        for (Field field : aClass.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object expectedFieldValue = field.get(expectedEvent);
                Object actualFieldValue = field.get(actualEvent);
                if (expectedFieldValue != null && actualFieldValue != null && expectedFieldValue.getClass().isArray()) {
                    if (!Arrays.deepEquals(new Object[]{expectedFieldValue}, new Object[]{actualFieldValue})) {
                        failedField = field;
                        failedFieldExpectedValue = expectedFieldValue;
                        failedFieldActualValue = actualFieldValue;
                        return false;
                    }
                } else if ((expectedFieldValue != null && !expectedFieldValue.equals(actualFieldValue))
                        || (expectedFieldValue == null && actualFieldValue != null)) {
                    failedField = field;
                    failedFieldExpectedValue = expectedFieldValue;
                    failedFieldActualValue = actualFieldValue;
                    return false;
                }
            } catch (IllegalAccessException e) {
                throw new FixtureExecutionException("Could not confirm event equality due to an exception", e);
            }
        }
        if (aClass.getSuperclass() != DomainEvent.class
                && aClass.getSuperclass() != EventBase.class
                && aClass.getSuperclass() != Object.class) {
            match = fieldsMatch(aClass.getSuperclass(), expectedEvent, actualEvent);
        }
        return match;
    }

    /**
     * Returns the field that failed comparison, if any. This value is only populated after {@link #matches(Object)} is
     * called and a mismatch has been detected.
     *
     * @return the field that failed comparison, if any
     */
    public Field getFailedField() {
        return failedField;
    }

    /**
     * Returns the expected value of a failed field comparison, if any. This value is only populated after {@link
     * #matches(Object)} is called and a mismatch has been detected.
     *
     * @return the expected value of the field that failed comparison, if any
     */
    public Object getFailedFieldExpectedValue() {
        return failedFieldExpectedValue;
    }

    /**
     * Returns the actual value of a failed field comparison, if any. This value is only populated after {@link
     * #matches(Object)} is called and a mismatch has been detected.
     *
     * @return the actual value of the field that failed comparison, if any
     */
    public Object getFailedFieldActualValue() {
        return failedFieldActualValue;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expected.getClass().getName());
        if (failedField != null) {
            description.appendText(" (failed on field '")
                       .appendText(failedField.getName())
                       .appendText("')");
        }
    }
}
