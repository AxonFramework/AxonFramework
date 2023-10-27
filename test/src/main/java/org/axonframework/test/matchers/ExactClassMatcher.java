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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Matcher testing for exact classes.
 *
 * @author Yoann CAPLAIN
 * @since 4.6.2
 */
public class ExactClassMatcher<T> extends TypeSafeMatcher<T> {

    private final Class<T> expectedClass;

    /**
     * Construct a {@link ExactClassMatcher} that will match an {@code actual} value with the given {@code expectedClass}.
     *
     * @param expectedClass The object class to match with during {@link #matches(Object)}.
     * @return a matcher that matches based on the class
     */
    public static <T> Matcher<T> exactClassOf(Class<T> expectedClass) {
        return new ExactClassMatcher<T>(expectedClass);
    }

    /**
     * Construct a {@link ExactClassMatcher} that will match an {@code actual} value with the given {@code expectedClass}.
     *
     * @param expectedClass The object class to match with during {@link #matches(Object)}.
     */
    public ExactClassMatcher(Class<T> expectedClass) {
        assertNonNull(expectedClass, "The expected class should be non-null.");
        this.expectedClass = expectedClass;
    }

    @Override
    protected boolean matchesSafely(T item) {
        return expectedClass.equals(item.getClass());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expectedClass.getName());
        description.appendText(" does not match with the actual type.");
    }
}
