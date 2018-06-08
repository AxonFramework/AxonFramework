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

import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;

import java.time.Instant;
import java.util.Objects;

/**
 * Holds the data regarding deadline schedule.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class ScheduleDeadlineInfo implements Comparable<ScheduleDeadlineInfo> {

    private final Instant scheduleTime;
    private final SimpleScheduleToken scheduleToken;
    private final int counter;
    private final Object deadlineInfo;
    private final ScopeDescriptor deadlineScope;

    /**
     * Initializes the ScheduleDeadlineInfo.
     *
     * @param scheduleTime    The time at which the deadline is scheduled
     * @param scheduleToken   The token representing deadline schedule, it can be used to cancel the deadline
     * @param counter         Used to differentiate two deadlines scheduled at the same time
     * @param deadlineInfo    Details about the deadline
     * @param deadlineScope Context in which the deadline is scheduled
     */
    public ScheduleDeadlineInfo(Instant scheduleTime, SimpleScheduleToken scheduleToken, int counter,
                                Object deadlineInfo, ScopeDescriptor deadlineScope) {
        this.scheduleTime = scheduleTime;
        this.scheduleToken = scheduleToken;
        this.counter = counter;
        this.deadlineInfo = deadlineInfo;
        this.deadlineScope = deadlineScope;
    }

    /**
     * @return the time at which the deadline is scheduled
     */
    public Instant getScheduleTime() {
        return scheduleTime;
    }

    /**
     * @return the token representing schedule, it can be used to cancel the deadline
     */
    public SimpleScheduleToken getScheduleToken() {
        return scheduleToken;
    }

    /**
     * @return the counter used to differentiate two deadlines scheduled at the same time
     */
    public int getCounter() {
        return counter;
    }

    /**
     * @return the context in which the deadline is scheduled
     */
    public ScopeDescriptor getDeadlineScope() {
        return deadlineScope;
    }

    /**
     * @return the {@link DeadlineMessage} constructed of {@code deadlineInfo}
     */
    public DeadlineMessage deadlineMessage() {
        return GenericDeadlineMessage.asDeadlineMessage(deadlineInfo);
    }

    @Override
    public int compareTo(ScheduleDeadlineInfo other) {
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
        ScheduleDeadlineInfo that = (ScheduleDeadlineInfo) o;
        return counter == that.counter &&
                Objects.equals(scheduleTime, that.scheduleTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleTime, counter);
    }
}
