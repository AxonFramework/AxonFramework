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

package org.axonframework.test.eventscheduler;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.ScheduledEvent;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.ReadableInstant;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class StubEventScheduler implements EventScheduler {

    private final NavigableSet<StubScheduleToken> scheduledEvents = new TreeSet<StubScheduleToken>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private DateTime currentDateTime;

    public StubEventScheduler() {
        currentDateTime = new DateTime();
    }

    public StubEventScheduler(ReadableInstant currentDateTime) {
        this.currentDateTime = new DateTime(currentDateTime);
    }

    @Override
    public ScheduleToken schedule(DateTime triggerDateTime, ApplicationEvent event) {
        StubScheduleToken token = new StubScheduleToken(triggerDateTime, event, counter.getAndIncrement());
        scheduledEvents.add(token);
        return token;
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, ApplicationEvent event) {
        DateTime scheduleTime = currentDateTime.plus(triggerDuration);
        StubScheduleToken token = new StubScheduleToken(scheduleTime, event, counter.getAndIncrement());
        scheduledEvents.add(token);
        return token;
    }

    @Override
    public ScheduleToken schedule(ScheduledEvent event) {
        StubScheduleToken token = new StubScheduleToken(event.getScheduledTime(), event, counter.getAndIncrement());
        scheduledEvents.add(token);
        return token;
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!(scheduleToken instanceof StubScheduleToken)) {
            throw new IllegalArgumentException("Wrong token type. This token was not provided by this scheduler");
        }
        scheduledEvents.remove(scheduleToken);
    }

    public List<ScheduledItem> getScheduledItems() {
        return new ArrayList<ScheduledItem>(scheduledEvents);
    }

    public DateTime getCurrentDateTime() {
        return currentDateTime;
    }

    public ApplicationEvent advanceToNextTrigger() {
        if (scheduledEvents.isEmpty()) {
            throw new NoSuchElementException("There are no scheduled events");
        }
        StubScheduleToken nextItem = scheduledEvents.pollFirst();
        if (nextItem.getScheduleTime().isAfter(currentDateTime)) {
            currentDateTime = nextItem.getScheduleTime();
        }
        return nextItem.getEvent();
    }

    public List<ApplicationEvent> advanceTime(DateTime newDateTime) {
        List<ApplicationEvent> triggeredEvents = new ArrayList<ApplicationEvent>();
        while (!scheduledEvents.isEmpty() && !scheduledEvents.first().getScheduleTime().isAfter(newDateTime)) {
            triggeredEvents.add(advanceToNextTrigger());
        }
        if (newDateTime.isAfter(currentDateTime)) {
            currentDateTime = newDateTime;
        }
        return triggeredEvents;
    }

    public List<ApplicationEvent> advanceTime(Duration duration) {
        return advanceTime(currentDateTime.plus(duration));
    }
}
