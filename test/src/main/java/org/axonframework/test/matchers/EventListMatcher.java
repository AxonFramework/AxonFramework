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
import org.hamcrest.BaseMatcher;

import java.util.List;

/**
 * Abstract implementation of a Matcher that will reject any matches against objects other than a List of Event.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public abstract class EventListMatcher extends BaseMatcher<List<? extends Event>> {

    @SuppressWarnings({"unchecked"})
    @Override
    public boolean matches(Object item) {
        return List.class.isInstance(item)
                && containsOnlyEvents((List) item)
                && matchesEventList((List<? extends Event>) item);
    }

    private boolean containsOnlyEvents(List items) {
        for (Object item : items) {
            if (!Event.class.isInstance(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Indicates whether the given List of Events meets the expectations of this matcher.
     *
     * @param events The events to verify
     * @return <code>true<code> if expectations are met, <code>false</code> otherwise.
     */
    public abstract boolean matchesEventList(List<? extends Event> events);
}
