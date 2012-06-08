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

package org.axonframework.test.saga;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.test.AxonAssertionError;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.axonframework.test.matchers.Matchers.*;
import static org.axonframework.test.saga.DescriptionUtils.describe;

/**
 * Helper class for validating events published on a given EventBus.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class EventValidator implements EventListener {

    private final List<EventMessage> publishedEvents = new ArrayList<EventMessage>();
    private final EventBus eventBus;

    /**
     * Initializes the event validator to monitor the given <code>eventBus</code>.
     *
     * @param eventBus the eventbus to monitor
     */
    public EventValidator(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Asserts that events have been published matching the given <code>matcher</code>.
     *
     * @param matcher The matcher that will validate the actual events
     */
    public void assertPublishedEventsMatching(Matcher<List<? extends EventMessage>> matcher) {
        if (!matcher.matches(publishedEvents)) {
            StringDescription expectedDescription = new StringDescription();
            StringDescription actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(publishedEvents, actualDescription);
            throw new AxonAssertionError(format("Published events did not match.\nExpected:\n<%s>\n\nGot:\n<%s>\n",
                                                expectedDescription, actualDescription));
        }
    }

    /**
     * Assert that the given <code>expected</code> events have been published.
     *
     * @param expected the events that must have been published.
     */
    public void assertPublishedEvents(Object... expected) {
        assertPublishedEventsMatching(eventPayloadsMatching(exactSequenceOf(createEqualToMatchers(expected))));
    }

    @Override
    public void handle(EventMessage event) {
        publishedEvents.add(event);
    }

    /**
     * Starts recording event published by the event bus.
     */
    public void startRecording() {
        eventBus.subscribe(this);
    }

    @SuppressWarnings({"unchecked"})
    private Matcher<Object>[] createEqualToMatchers(Object[] expected) {
        List<Matcher<?>> matchers = new ArrayList<Matcher<?>>(expected.length);
        for (Object event : expected) {
            matchers.add(equalTo(event));
        }
        return matchers.toArray(new Matcher[matchers.size()]);
    }
}
