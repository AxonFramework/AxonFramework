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

import java.util.Iterator;
import java.util.List;

/**
 * A matcher that will match if all the given {@code matchers} against the event in a list at their respective
 * index. That means the first matcher must match against the first event, the second matcher against the second event,
 * and so forth.
 * <p/>
 * If the number of Events is larger than the number of matchers, the excess events are not evaluated. Use {@link
 * Matchers#exactSequenceOf(org.hamcrest.Matcher[])} to match the sequence exactly. If there are more matchers
 * than Events, the remainder of matchers is evaluated against a {@code null} value.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class ExactSequenceMatcher<T> extends ListMatcher<T> {

    /**
     * Construct a matcher that will return true if all the given {@code matchers} match against the event with
     * the same index in a given List if Events.
     *
     * @param matchers The matchers that must match against at least one Event in the list.
     */
    @SafeVarargs
    public ExactSequenceMatcher(Matcher<? super T>... matchers) {
        super(matchers);
    }

    @Override
    public boolean matchesList(List<T> events) {
        Iterator<?> eventIterator = events.iterator();
        Iterator<Matcher<? super T>> matcherIterator = getMatchers().iterator();
        while (eventIterator.hasNext() && matcherIterator.hasNext()) {
            Matcher<? super T> matcher = matcherIterator.next();
            if (!matcher.matches(eventIterator.next())) {
                reportFailed(matcher);
                return false;
            }
        }
        return matchRemainder(matcherIterator);
    }

    @Override
    protected void describeCollectionType(Description description) {
        description.appendText("exact sequence");
    }
}
