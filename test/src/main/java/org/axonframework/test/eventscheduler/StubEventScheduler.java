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

package org.axonframework.test.eventscheduler;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.joda.time.ReadableInstant;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EventScheduler implementation that uses it's own concept of "Current Time" for the purpose of testing. Instead of
 * publishing events, it allows (test) classes to obtain a reference to scheduled events for a certain time frame.
 * <p/>
 * To obtain scheduled events, the conceptual "current time" of the Scheduler must be advanced. All events that have
 * been scheduled within the advanced time frame are returned. It is up to the calling class to decide what to do with
 * these events.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class StubEventScheduler implements EventScheduler, DateTimeUtils.MillisProvider {

    private final NavigableSet<StubScheduleToken> scheduledEvents = new TreeSet<StubScheduleToken>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private DateTime currentDateTime;

    /**
     * Creates an instance of the StubScheduler that uses the current date time as its conceptual "current time".
     * Unlike
     * the real "current time", the time of the Event Scheduler is fixed.
     */
    public StubEventScheduler() {
        this(null);
    }

    /**
     * Creates an instance of the StubScheduler that uses the given <code>currentDateTime</code> as its conceptual
     * "current time".
     *
     * @param currentDateTime The instant to use as current Date and Time
     */

    public StubEventScheduler(ReadableInstant currentDateTime) {
        this.currentDateTime = new DateTime(currentDateTime);
    }

    @Override
    public ScheduleToken schedule(DateTime triggerDateTime, Object event) {
        EventMessage eventMessage = GenericEventMessage.asEventMessage(event);
        StubScheduleToken token = new StubScheduleToken(triggerDateTime, eventMessage, counter.getAndIncrement());
        scheduledEvents.add(token);
        return token;
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        EventMessage eventMessage = GenericEventMessage.asEventMessage(event);
        DateTime scheduleTime = currentDateTime.plus(triggerDuration);
        StubScheduleToken token = new StubScheduleToken(scheduleTime, eventMessage, counter.getAndIncrement());
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

    /**
     * Returns a view of all the scheduled Events at the time this method is called.
     *
     * @return a view of all the scheduled Events at the time this method is called
     */
    public List<ScheduledItem> getScheduledItems() {
        return new ArrayList<ScheduledItem>(scheduledEvents);
    }

    /**
     * Returns the "Current Date Time" as used by the scheduler.
     *
     * @return the "Current Date Time" as used by the scheduler
     */
    public DateTime getCurrentDateTime() {
        return currentDateTime;
    }

    /**
     * Advances the "current time" of the scheduler to the next scheduled Event, and returns that event. In theory,
     * this
     * may cause "current time" to move backwards.
     *
     * @return the first event scheduled
     */
    public EventMessage advanceToNextTrigger() {
        if (scheduledEvents.isEmpty()) {
            throw new NoSuchElementException("There are no scheduled events");
        }
        StubScheduleToken nextItem = scheduledEvents.pollFirst();
        if (nextItem.getScheduleTime().isAfter(currentDateTime)) {
            currentDateTime = nextItem.getScheduleTime();
        }
        return nextItem.getEvent();
    }

    /**
     * Advance time to the given <code>newDateTime</code> and returns all events scheduled for publication until that
     * time.
     *
     * @param newDateTime The time to advance the "current time" of the scheduler to
     * @return A list of Events scheduled for publication on or before the new time
     */
    public List<EventMessage> advanceTime(DateTime newDateTime) {
        List<EventMessage> triggeredEvents = new ArrayList<EventMessage>();
        while (!scheduledEvents.isEmpty() && !scheduledEvents.first().getScheduleTime().isAfter(newDateTime)) {
            triggeredEvents.add(advanceToNextTrigger());
        }
        if (newDateTime.isAfter(currentDateTime)) {
            currentDateTime = newDateTime;
        }
        return triggeredEvents;
    }

    /**
     * Advance time by the given <code>duration</code> and returns all events scheduled for publication until that
     * time.
     *
     * @param duration The amount of time to advance the "current time" of the scheduler with
     * @return A list of Events scheduled for publication on or before the new time
     */
    public List<EventMessage> advanceTime(Duration duration) {
        return advanceTime(currentDateTime.plus(duration));
    }

    @Override
    public long getMillis() {
        return currentDateTime.getMillis();
    }
}
