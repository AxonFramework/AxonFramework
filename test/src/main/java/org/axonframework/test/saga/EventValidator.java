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

package org.axonframework.test.saga;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.FieldFilter;
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
public class EventValidator implements EventMessageHandler {

    private final List<EventMessage<?>> publishedEvents = new ArrayList<>();
    private final EventBus eventBus;
    private final FieldFilter fieldFilter;
    private boolean recording = false;

    /**
     * Initializes the event validator to monitor the given {@code eventBus}.
     *
     * @param eventBus    the event bus to monitor
     * @param fieldFilter the filter describing the Fields to include in a comparison
     */
    public EventValidator(EventBus eventBus, FieldFilter fieldFilter) {
        this.eventBus = eventBus;
        this.fieldFilter = fieldFilter;
    }

    /**
     * Asserts that events have been published matching the given {@code matcher}.
     *
     * @param matcher The matcher that will validate the actual events
     */
    public void assertPublishedEventsMatching(Matcher<? extends Iterable<?>> matcher) {
        if (!matcher.matches(publishedEvents)) {
            StringDescription expectedDescription = new StringDescription();
            StringDescription actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(publishedEvents, actualDescription);
            throw new AxonAssertionError(format("Published events did not match.\nExpected <%s>,\n but got <%s>\n",
                                                expectedDescription, actualDescription));
        }
    }

    /**
     * Assert that the given {@code expected} events have been published.
     *
     * @param expected the events that must have been published.
     */
    public void assertPublishedEvents(Object... expected) {
        if (publishedEvents.size() != expected.length) {
            throw new AxonAssertionError(format(
                    "Got wrong number of events published.\nExpected <%s>,\n but got <%s>.",
                    expected.length, publishedEvents.size()
            ));
        }

        assertPublishedEventsMatching(payloadsMatching(exactSequenceOf(createEqualToMatchers(expected))));
    }

    @Override
    public Object handleSync(EventMessage<?> event) {
        publishedEvents.add(event);
        return null;
    }

    /**
     * Starts recording event published by the event bus.
     */
    public void startRecording() {
        if (!recording) {
            eventBus.subscribe(eventMessages -> eventMessages.forEach(this::handleSync));
            recording = true;
        }
        publishedEvents.clear();
    }

    @SuppressWarnings({"unchecked"})
    private Matcher<Object>[] createEqualToMatchers(Object[] expected) {
        List<Matcher<?>> matchers = new ArrayList<>(expected.length);
        for (Object event : expected) {
            matchers.add(deepEquals(unwrapEvent(event), fieldFilter));
        }
        return matchers.toArray(new Matcher[0]);
    }

    /**
     * Unwrap the given {@code event} if it's an instance of {@link EventMessage}. Otherwise, return the given
     * {@code event} as is.
     *
     * @param event either an {@link EventMessage} or the payload of an EventMessage
     * @return the given {@code event} as is or the {@link EventMessage#getPayload()}
     */
    private Object unwrapEvent(Object event) {
        return event instanceof EventMessage ? ((EventMessage) event).getPayload() : event;
    }
}
