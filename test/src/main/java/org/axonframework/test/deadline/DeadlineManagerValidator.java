/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.test.deadline;

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.FieldFilter;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.test.matchers.Matchers.*;

/**
 * Helps validation of deadline mechanism provided by {@link StubDeadlineManager}.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class DeadlineManagerValidator {

    private final StubDeadlineManager deadlineManager;
    private final FieldFilter fieldFilter;

    /**
     * Instantiates this validator with {@code deadlineManager} which is used for validation purposes and {@code
     * fieldFilter}.
     *
     * @param deadlineManager Stub implementation of deadline manager which keeps track of scheduled and met deadlines
     * @param fieldFilter     Indicates whether any given Field should be accepted for processing or not
     */
    public DeadlineManagerValidator(StubDeadlineManager deadlineManager,
                                    FieldFilter fieldFilter) {
        this.deadlineManager = deadlineManager;
        this.fieldFilter = fieldFilter;
    }

    /**
     * Asserts that a deadline scheduled after given {@code duration} matches the given {@code matcher}.
     *
     * @param duration The delay expected before the deadline is met
     * @param matcher  The matcher that must match with the deadline scheduled at the given time
     */
    public void assertScheduledDeadlineMatching(Duration duration, Matcher<?> matcher) {
        Instant targetTime = deadlineManager.getCurrentDateTime().plus(duration);
        assertScheduledDeadlineMatching(targetTime, matcher);
    }

    /**
     * Asserts that a deadline scheduled at the given {@code scheduledTime} matches the given {@code matcher}.
     *
     * @param scheduledTime The time at which the deadline should be met
     * @param matcher       The matcher that must match with the deadline scheduled at the given time
     */
    public void assertScheduledDeadlineMatching(Instant scheduledTime, Matcher<?> matcher) {
        List<ScheduledDeadlineInfo> scheduledDeadlines = deadlineManager.getScheduledDeadlines();
        for (ScheduledDeadlineInfo scheduledDeadline : scheduledDeadlines) {
            if (scheduledDeadline.getScheduleTime().equals(scheduledTime) &&
                    matcher.matches(scheduledDeadline.deadlineMessage())) {
                return;
            }
        }
        Description expected = new StringDescription();
        Description actual = new StringDescription();
        matcher.describeTo(expected);
        describe(scheduledDeadlines, actual);
        throw new AxonAssertionError(format(
                "Did not find a deadline at the given deadline manager. \nExpected:\n<%s> at <%s>\nGot:%s\n",
                expected, scheduledTime, actual));
    }

    /**
     * Asserts that deadlines have been met (which have passed in time) match the given {@code matcher}.
     *
     * @param matcher The matcher that will validate the actual deadlines
     */
    public void assertDeadlinesMetMatching(Matcher<? extends Iterable<?>> matcher) {
        List<ScheduledDeadlineInfo> deadlinesMet = deadlineManager.getDeadlinesMet();
        List<DeadlineMessage> deadlineMessages = deadlinesMet.stream()
                                                             .map(ScheduledDeadlineInfo::deadlineMessage)
                                                             .collect(Collectors.toList());
        if (!matcher.matches(deadlineMessages)) {
            Description expected = new StringDescription();
            Description actual = new StringDescription();
            matcher.describeTo(expected);
            describe(deadlinesMet, actual);
            throw new AxonAssertionError(format(
                    "Expected deadlines were not met at the given deadline manager. \nExpected:\n<%s>\nGot:%s\n",
                    expected, actual));
        }
    }

    /**
     * Asserts that the given {@code expected} deadlines have been met (which have passed in time).
     *
     * @param expected The deadlines that must have been met
     */
    public void assertDeadlinesMet(Object... expected) {
        List<ScheduledDeadlineInfo> deadlinesMet = deadlineManager.getDeadlinesMet();
        if (deadlinesMet.size() != expected.length) {
            throw new AxonAssertionError(format("Got wrong number of deadlines met. Expected <%s>, got <%s>",
                                                expected.length,
                                                deadlinesMet.size()));
        }
        assertDeadlinesMetMatching(payloadsMatching(exactSequenceOf(createEqualToMatchers(expected))));
    }

    /**
     * Asserts that no deadlines are scheduled.
     */
    public void assertNoScheduledDeadlines() {
        List<ScheduledDeadlineInfo> scheduledDeadlines = deadlineManager.getScheduledDeadlines();
        if (scheduledDeadlines != null && !scheduledDeadlines.isEmpty()) {
            throw new AxonAssertionError("Expected no scheduled deadlines, got " + scheduledDeadlines.size());
        }
    }

    private void describe(List<ScheduledDeadlineInfo> scheduleDeadlines, Description description) {
        if (scheduleDeadlines.isEmpty()) {
            description.appendText("\n<no scheduled events>");
        }
        for (ScheduledDeadlineInfo item : scheduleDeadlines) {
            description.appendText("\n<")
                       .appendText(item.deadlineMessage().toString())
                       .appendText("> at <")
                       .appendText(formatInstant(item.getScheduleTime()))
                       .appendText(">");
        }
    }

    @SuppressWarnings("unchecked")
    private Matcher<Object>[] createEqualToMatchers(Object[] expected) {
        List<Matcher<?>> matchers = new ArrayList<>(expected.length);
        for (Object deadline : expected) {
            matchers.add(equalTo(deadline, fieldFilter));
        }
        return matchers.toArray(new Matcher[0]);
    }
}
