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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Objects;

import static org.axonframework.common.ReflectionUtils.hasEqualsMethod;

/**
 * A {@link BaseMatcher} implementation that first matches based on {@link Object#equals(Object)}. When this fails and
 * {@code equals()} is not implemented by {@code T}, the fields are matched for equality. If this fails due to
 * inaccessibility of the class or its fields, this matcher will not match.
 *
 * @param <T> The type of object to match.
 * @author Steven van Beelen
 * @since 4.5.10
 */
public class DeepEqualsMatcher<T> extends BaseMatcher<T> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final T expected;
    private final FieldFilter filter;

    private boolean noneMatchingTypes = false;
    private boolean noneMatchingEquals = false;

    private Field failedField;
    private Object failedFieldExpected;
    private Object failedFieldActual;
    private boolean fieldMatchingFailed;


    /**
     * Construct a {@link DeepEqualsMatcher} that will match an {@code actual} value with the given {@code expected}.
     *
     * @param expected The object to match with during {@link #matches(Object)}.
     */
    public DeepEqualsMatcher(T expected) {
        this(expected, AllFieldsFilter.instance());
    }

    /**
     * Construct a {@link DeepEqualsMatcher} that will match an {@code actual} value with the given {@code expected}.
     *
     * @param expected The object to match with during {@link #matches(Object)}.
     * @param filter   The filter describing the fields to include or exclude in the comparison.
     */
    public DeepEqualsMatcher(T expected, FieldFilter filter) {
        this.expected = expected;
        this.filter = filter;
    }

    @Override
    public boolean matches(Object actual) {
        if (!matchingTypes(actual)) {
            noneMatchingTypes = true;
            return false;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (hasEqualsMethod(actual.getClass())) {
            // Expected does not equal actual, and equals is implemented. Hence, we should not perform field equality.
            noneMatchingEquals = true;
            return false;
        }
        return matchingFields(expected.getClass(), expected, actual);
    }

    private boolean matchingTypes(Object actual) {
        return expected.getClass().isInstance(actual) && expected.getClass().equals(actual.getClass());
    }

    private boolean matchingFields(Class<?> aClass, Object expectedValue, Object actual) {
        boolean match = true;
        for (Field field : aClass.getDeclaredFields()) {
            if (filter.accept(field)) {
                try {
                    field.setAccessible(true);
                    Object expectedFieldValue = field.get(expectedValue);
                    Object actualFieldValue = field.get(actual);
                    if (!Objects.deepEquals(expectedFieldValue, actualFieldValue)) {
                        failedField = field;
                        failedFieldExpected = expectedFieldValue;
                        failedFieldActual = actualFieldValue;
                        return false;
                    }
                } catch (Exception e) {
                    logger.warn("Could not confirm object field equality due to an exception. "
                                        + "Assuming objects are not equal.", e);
                    fieldMatchingFailed = true;
                    return false;
                }
            }
        }
        if (aClass.getSuperclass() != Object.class) {
            match = matchingFields(aClass.getSuperclass(), expectedValue, actual);
        }
        return match;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expected.getClass().getName());
        if (noneMatchingTypes) {
            description.appendText(" does not match with the actual type.");
        } else if (noneMatchingEquals) {
            description.appendText(" does not equal with the actual instance.");
        } else if (failedField != null) {
            description.appendText(" (failed on field '")
                       .appendText(failedField.getName())
                       .appendText("').")
                       .appendText(" Expected field value [")
                       .appendValue(failedFieldExpected)
                       .appendText("], but actual field value was [")
                       .appendValue(failedFieldActual)
                       .appendText("].");
        } else if (fieldMatchingFailed) {
            description.appendText(" failed during field equality.");
        }
    }
}
