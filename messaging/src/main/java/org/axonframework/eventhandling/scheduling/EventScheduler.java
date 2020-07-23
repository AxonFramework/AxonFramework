/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling.scheduling;

import java.time.Duration;
import java.time.Instant;

/**
 * Interface towards a mechanism capable of scheduling the publication of events. The accuracy of the publication time
 * depends on the exact implementation used.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface EventScheduler {

    /**
     * Schedule the given {@code event} for publication at the given {@code triggerDateTime}. The returned
     * ScheduleToken can be used to cancel the planned publication.
     * <p/>
     * The given {@code event} may be any object, as well as an EventMessage. In the latter case, the instance
     * provided is the donor for the payload and Meta Data of the actual message being dispatched. In the former case,
     * the given {@code event} will be wrapped as the payload of an EventMessage.
     *
     * @param triggerDateTime The moment to trigger publication of the event
     * @param event           The event to publish
     * @return the token to use when cancelling the schedule
     */
    ScheduleToken schedule(Instant triggerDateTime, Object event);

    /**
     * Schedule the given {@code event} for publication after the given {@code triggerDuration}.  The
     * returned ScheduleToken can be used to cancel the planned publication.
     * <p/>
     * The given {@code event} may be any object, as well as an EventMessage. In the latter case, the instance
     * provided is the donor for the payload and Meta Data of the actual message being dispatched. In the former case,
     * the given {@code event} will be wrapped as the payload of an EventMessage.
     *
     * @param triggerDuration The amount of time to wait before publishing the event
     * @param event           The event to publish
     * @return the token to use when cancelling the schedule
     */
    ScheduleToken schedule(Duration triggerDuration, Object event);

    /**
     * Cancel the publication of a scheduled event. If the events has already been published, this method does nothing.
     *
     * @param scheduleToken the token returned when the event was scheduled
     * @throws IllegalArgumentException if the token belongs to another scheduler
     */
    void cancelSchedule(ScheduleToken scheduleToken);

    /**
     * Cancel a scheduled event and schedule another in its place.
     * <p/>
     * Convenience method around {@link #cancelSchedule(ScheduleToken)} and {@link #schedule(Duration, Object)}.
     *
     * @param scheduleToken   the token returned when the event was scheduled, might be null
     * @param triggerDuration The amount of time to wait before publishing the event
     * @param event           The event to publish
     * @throws IllegalArgumentException if the token belongs to another scheduler
     * @return the token to use when cancelling the schedule
     */
    default ScheduleToken reschedule(ScheduleToken scheduleToken, Duration triggerDuration, Object event) {
        if (null != scheduleToken) {
            cancelSchedule(scheduleToken);
        }
        return schedule(triggerDuration, event);
    }

    /**
     * Cancel a scheduled event and schedule another in its place.
     * <p/>
     * Convenience method around {@link #cancelSchedule(ScheduleToken)} and {@link #schedule(Duration, Object)}.
     *
     * @param scheduleToken   the token returned when the event was scheduled, might be null
     * @param instant The moment in time to wait before publishing the event
     * @param event           The event to publish
     * @throws IllegalArgumentException if the token belongs to another scheduler
     * @return the token to use when cancelling the schedule
     */
    default ScheduleToken reschedule(ScheduleToken scheduleToken, Instant instant, Object event) {
        if (null != scheduleToken) {
            cancelSchedule(scheduleToken);
        }
        return schedule(instant, event);
    }

    /**
     * Shuts down this event scheduler.
     */
    default void shutdown() {
    }
}
