/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Matcher that will match an Object if all the fields on that Object contain values equal to the same field in the
 * expected instance.
 *
 * @param <T> The type of object
 * @author Allard Buijze
 * @since 1.1
 */
public class EqualFieldsMatcher<T> extends BaseMatcher<T> {

    private final T expected;
    private final FieldFilter filter;
    private Field failedField;
    private Object failedFieldExpectedValue;
    private Object failedFieldActualValue;

    /**
     * Initializes an EqualFieldsMatcher that will match an object with equal properties as the given {@code expected}
     * object.
     *
     * @param expected The expected object
     */
    public EqualFieldsMatcher(T expected) {
        this(expected, AllFieldsFilter.instance());
    }

    /**
     * Initializes an EqualFieldsMatcher that will match an object with equal properties as the given {@code expected}
     * object.
     *
     * @param expected The expected object
     * @param filter   The filter describing the fields to include in the comparison
     */
    public EqualFieldsMatcher(T expected, FieldFilter filter) {
        this.expected = expected;
        this.filter = filter;
    }

    @Override
    public boolean matches(Object actual) {
        return expected.getClass().isInstance(actual) && matchesSafely(actual);
    }

    private boolean matchesSafely(Object actual) {
        return expected.getClass().equals(actual.getClass())
                && fieldsMatch(expected.getClass(), expected, actual);
    }

    private boolean fieldsMatch(Class<?> aClass, Object expectedValue, Object actual) {
        boolean match = true;
        for (Field field : aClass.getDeclaredFields()) {
            if (filter.accept(field)) {
                field.setAccessible(true);
                try {
                    Object expectedFieldValue = field.get(expectedValue);
                    Object actualFieldValue = field.get(actual);
                    if (!Objects.deepEquals(expectedFieldValue, actualFieldValue)) {
                        failedField = field;
                        failedFieldExpectedValue = expectedFieldValue;
                        failedFieldActualValue = actualFieldValue;
                        return false;
                    }
                } catch (IllegalAccessException e) {
                    throw new MatcherExecutionException("Could not confirm object equality due to an exception", e);
                }
            }
        }
        if (aClass.getSuperclass() != Object.class) {
            match = fieldsMatch(aClass.getSuperclass(), expectedValue, actual);
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
     * Returns the expected value of a failed field comparison, if any. This value is only populated after
     * {@link #matches(Object)} is called and a mismatch has been detected.
     *
     * @return the expected value of the field that failed comparison, if any
     */
    public Object getFailedFieldExpectedValue() {
        return failedFieldExpectedValue;
    }

    /**
     * Returns the actual value of a failed field comparison, if any. This value is only populated after
     * {@link #matches(Object)} is called and a mismatch has been detected.
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
