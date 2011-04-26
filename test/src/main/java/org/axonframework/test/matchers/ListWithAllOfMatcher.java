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

import org.axonframework.domain.Event;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.List;

/**
 * A matcher that will match if all the given <code>matchers</code> match against at least one Event in a given List of
 * Events.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class ListWithAllOfMatcher extends EventListMatcher {

    private final Matcher<? extends Event>[] matchers;
    private Matcher<? extends Event> failedMatcher;

    /**
     * Construct a matcher that will return true if all the given <code>matchers</code> match against at least one
     * Event
     * in any given List.
     *
     * @param matchers The matchers that must match against at least one Event in the list.
     */
    public ListWithAllOfMatcher(Matcher<? extends Event>... matchers) {
        this.matchers = matchers;
    }

    @Override
    public boolean matchesEventList(List<? extends Event> events) {
        for (Matcher<? extends Event> matcher : matchers) {
            boolean match = false;
            for (Event event : events) {
                if (matcher.matches(event)) {
                    match = true;
                }
            }
            if (!match) {
                failedMatcher = matcher;
                return false;
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("list with all of: ");
        for (int t = 0; t < matchers.length; t++) {
            if (t != 0 && t < matchers.length - 1) {
                description.appendText(", ");
            } else if (t == matchers.length - 1) {
                description.appendText(" and ");
            }
            matchers[t].describeTo(description);
            if (matchers[t].equals(failedMatcher)) {
                description.appendText(" (FAILED!)");
            }
        }
    }
}
