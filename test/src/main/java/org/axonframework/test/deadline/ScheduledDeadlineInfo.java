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
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.messaging.ScopeDescriptor;

import java.time.Instant;
import java.util.Objects;

/**
 * Holds the data regarding deadline schedule.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public class ScheduledDeadlineInfo implements Comparable<ScheduledDeadlineInfo> {

    private final Instant scheduleTime;
    private final String deadlineName;
    private final String scheduleId;
    private final int counter;
    private final Object messageOrPayload;
    private final ScopeDescriptor deadlineScope;

    /**
     * Instantiates a ScheduledDeadlineInfo.
     *
     * @param scheduleTime     The time as an {@link Instant} at which the deadline is scheduled
     * @param deadlineName     A {@link String} denoting the name of the deadline; can be used together with the
     *                         {@code scheduleId} to cancel the deadline
     * @param scheduleId       A {@link String} identifier representing the scheduled deadline; can be used together
     *                         with the {@code deadlineName} to cancel the deadline
     * @param counter          Used to differentiate two deadlines scheduled at the same time
     * @param messageOrPayload The payload of the scheduled deadline. Might be {@code null} if none is provided
     * @param deadlineScope    A description of the {@link org.axonframework.messaging.Scope} in which the deadline is
     *                         scheduled
     */
    public ScheduledDeadlineInfo(Instant scheduleTime,
                                 String deadlineName,
                                 String scheduleId,
                                 int counter,
                                 Object messageOrPayload,
                                 ScopeDescriptor deadlineScope) {
        this.scheduleTime = scheduleTime;
        this.deadlineName = deadlineName;
        this.scheduleId = scheduleId;
        this.counter = counter;
        this.messageOrPayload = messageOrPayload;
        this.deadlineScope = deadlineScope;
    }

    /**
     * Retrieve the time as an {@link Instant} at which the deadline is scheduled.
     *
     * @return the time as an {@link Instant} at which the deadline is scheduled
     */
    public Instant getScheduleTime() {
        return scheduleTime;
    }

    /**
     * Retrieve a {@link String} denoting the name of the deadline; can be used together with the {@code scheduleId} to
     * cancel the deadline.
     *
     * @return a {@link String} denoting the name of the deadline; can be used together with the {@code scheduleId} to
     * cancel the deadline
     */
    public String getDeadlineName() {
        return deadlineName;
    }

    /**
     * Retrieve a {@link String} identifier representing the scheduled deadline; can be used together with the
     * {@code deadlineName} to cancel the deadline.
     *
     * @return a {@link String} identifier representing the scheduled deadline; can be used together with the
     * {@code deadlineName} to cancel the deadline
     */
    public String getScheduleId() {
        return scheduleId;
    }

    /**
     * Retrieve the counter used to differentiate two deadlines scheduled at the same time.
     *
     * @return the counter used to differentiate two deadlines scheduled at the same time
     */
    public int getCounter() {
        return counter;
    }

    /**
     * Retrieve the payload of the scheduled deadline. Might be {@code null} if none is provided.
     *
     * @return the payload of the scheduled deadline. Might be {@code null} if none is provided
     */
    public Object getMessageOrPayload() {
        return messageOrPayload;
    }

    /**
     * Retrieve a description of the {@link org.axonframework.messaging.Scope} in which the deadline is scheduled.
     *
     * @return a description of the {@link org.axonframework.messaging.Scope} in which the deadline is scheduled
     */
    public ScopeDescriptor getDeadlineScope() {
        return deadlineScope;
    }

    /**
     * Retrieve a {@link DeadlineMessage} constructed out of the {@code deadlineName} and {@code deadlineInfo}.
     *
     * @return a {@link DeadlineMessage} constructed out of the {@code deadlineName} and {@code deadlineInfo}
     */
    public DeadlineMessage deadlineMessage() {
        return GenericDeadlineMessage.asDeadlineMessage(deadlineName, messageOrPayload);
    }

    @Override
    public int compareTo(ScheduledDeadlineInfo other) {
        if (scheduleTime.equals(other.scheduleTime)) {
            return Integer.compare(counter, other.counter);
        }
        return scheduleTime.compareTo(other.scheduleTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScheduledDeadlineInfo that = (ScheduledDeadlineInfo) o;
        return counter == that.counter &&
                Objects.equals(scheduleTime, that.scheduleTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleTime, counter);
    }
}
