/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.List;

/**
 * A matcher that will match if all the given {@code matchers} match against at least one item in a given List.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class ListWithAllOfMatcher extends ListMatcher {

    /**
     * Construct a matcher that will return true if all the given {@code matchers} match against at least one
     * item in any given List.
     *
     * @param matchers The matchers that must match against at least one item in the list.
     */
    public ListWithAllOfMatcher(Matcher... matchers) {
        super(matchers);
    }

    @Override
    public boolean matchesList(List<?> items) {
        for (Matcher matcher : getMatchers()) {
            boolean match = false;
            for (Object item : items) {
                if (matcher.matches(item)) {
                    match = true;
                }
            }
            if (!match) {
                reportFailed(matcher);
                return false;
            }
        }
        return true;
    }


    @Override
    protected void describeCollectionType(Description description) {
        description.appendText("all");
    }
}
