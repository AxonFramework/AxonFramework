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

import java.util.Objects;

/**
 * Matcher testing for object equality as per {@link Objects#equals(Object o)}
 *
 * @author bliessens
 * @since 3.3
 */
public class EqualsMatcher<T> extends TypeSafeMatcher<T> {

    private final T expectedValue;

    public static <T> Matcher<T> equalTo(T item) {
        return new EqualsMatcher<T>(item);
    }

    public EqualsMatcher(T expected) {
        this.expectedValue = expected;
    }

    @Override
    protected boolean matchesSafely(T item) {
        return Objects.equals(expectedValue, item);
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(expectedValue);
    }
}
