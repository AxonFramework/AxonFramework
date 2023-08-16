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

package org.axonframework.test.eventscheduler;

import org.axonframework.test.AxonAssertionError;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.lang.String.format;
import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Helper class for validating events scheduled in a given event scheduler.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class EventSchedulerValidator {

    private final StubEventScheduler eventScheduler;

    /**
     * Initializes the validator to validate the state of the given {@code eventScheduler}.
     *
     * @param eventScheduler The event scheduler to monitor
     */
    public EventSchedulerValidator(StubEventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    /**
     * Asserts that an event matching the given {@code matcher} is scheduled for publication after the given {@code
     * duration}.
     *
     * @param duration The delay expected before the event is published
     * @param matcher  The matcher that must match with the event scheduled at the given time
     */
    public void assertScheduledEventMatching(Duration duration, Matcher<?> matcher) {
        Instant targetTime = eventScheduler.getCurrentDateTime().plus(duration);
        assertScheduledEventMatching(targetTime, matcher);
    }

    /**
     * Asserts that an event matching the given {@code matcher} is scheduled for publication at the given {@code
     * scheduledTime}.
     *
     * @param scheduledTime the time at which the event should be published
     * @param matcher       The matcher that must match with the event scheduled at the given time
     */
    public void assertScheduledEventMatching(Instant scheduledTime, Matcher<?> matcher) {
        List<ScheduledItem> schedule = eventScheduler.getScheduledItems();
        for (ScheduledItem item : schedule) {
            if (item.getScheduleTime().equals(scheduledTime) && matcher.matches(item.getEvent())) {
                return;
            }
        }
        Description expected = new StringDescription();
        Description actual = new StringDescription();
        matcher.describeTo(expected);
        describe(eventScheduler.getScheduledItems(), actual);
        throw new AxonAssertionError(String.format(
                "Did not find an event at the given schedule. \nExpected:\n<%s> at <%s>\nGot:%s\n",
                expected, scheduledTime, actual
        ));
    }

    private void describe(List<ScheduledItem> scheduledItems, Description description) {
        if (scheduledItems.isEmpty()) {
            description.appendText("\n<no scheduled events>");
        }
        for (ScheduledItem item : scheduledItems) {
            description.appendText("\n<")
                       .appendText(item.getEvent().toString())
                       .appendText("> at <")
                       .appendText(formatInstant(item.getScheduleTime()))
                       .appendText(">");
        }
    }

    /**
     * Asserts that <b>no</b> event matching the given {@code matcher} has been scheduled after the given {@code
     * duration}.
     *
     * @param duration the delay within which no event matching the given {@code matcher} is expected
     * @param matcher  the matcher defining the event which should not be scheduled after the given {@code duration}
     */
    public void assertNoScheduledEventMatching(Duration duration, Matcher<?> matcher) {
        Instant targetTime = eventScheduler.getCurrentDateTime().plus(duration);
        assertNoScheduledEventMatching(targetTime, matcher);
    }

    /**
     * Asserts that <b>no</b> event matching the given {@code matcher} has been scheduled at the given {@code
     * scheduledTime}.
     *
     * @param scheduledTime the time at which no event matching the given {@code matcher} is expected
     * @param matcher       the matcher defining the event which should not be scheduled at the given {@code
     *                      scheduledTime}
     */
    public void assertNoScheduledEventMatching(Instant scheduledTime, Matcher<?> matcher) {
        for (ScheduledItem scheduledItem : eventScheduler.getScheduledItems()) {
            if (scheduledItem.getScheduleTime().equals(scheduledTime) &&
                    matcher.matches(scheduledItem.getEvent())) {
                Description unexpected = new StringDescription();
                matcher.describeTo(unexpected);
                throw new AxonAssertionError(format(
                        "Unexpected matching event found at the given schedule. \nGot:%s\n",
                        unexpected
                ));
            }
        }
    }

    /**
     * Asserts that no events are scheduled for publication.
     */
    public void assertNoScheduledEvents() {
        List<ScheduledItem> scheduledItems = eventScheduler.getScheduledItems();
        if (scheduledItems != null && !scheduledItems.isEmpty()) {
            throw new AxonAssertionError("Expected no scheduled events, got " + scheduledItems.size());
        }
    }
}
