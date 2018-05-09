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

package org.axonframework.deadline;

import org.axonframework.eventhandling.scheduling.ScheduleToken;

import java.time.Duration;
import java.time.Instant;

/**
 * Contract for deadline managers. There are two groups of methods for scheduling - ones which accept external {@link
 * ScheduleToken} and ones generate the token themselves and return it to the caller. For callers that use external
 * {@link ScheduleToken}, it is recommended to use {@link #generateToken()} method in order to generate one.
 *
 * @author Milan Savic
 * @since 3.3
 */
public interface DeadlineManager {

    /**
     * Schedules a deadline at given {@code triggerDateTime} with provided context. The returned ScheduleToken can be
     * used to cancel the scheduled deadline.
     * <p>
     * The given {@code deadlineInfo} may be any object, as well as a DeadlineMessage. In the latter case, the instance
     * provided is the donor for the payload and Meta Data of the actual deadline being used. In the former case, the
     * given {@code deadlineInfo} will be wrapped as the payload of a DeadlineMessage.
     * </p>
     *
     * @param triggerDateTime The moment to trigger the deadline handling
     * @param deadlineContext The context in which deadline is scheduled - saga or aggregate, id, and target type
     * @param deadlineInfo    Details about the deadline
     * @param <T>             The type of the deadline details
     * @return the token to use when cancelling the schedule
     *
     * @see DeadlineContext
     */
    default <T> ScheduleToken schedule(Instant triggerDateTime, DeadlineContext deadlineContext, T deadlineInfo) {
        ScheduleToken scheduleToken = generateToken();
        schedule(triggerDateTime, deadlineContext, deadlineInfo, scheduleToken);
        return scheduleToken;
    }

    /**
     * Schedules a deadline after the given {@code triggerDuration} with provided context. The returned ScheduleToken
     * can be used to cancel the scheduled deadline.
     * <p>
     * The given {@code deadlineInfo} may be any object, as well as a DeadlineMessage. In the latter case, the instance
     * provided is the donor for the payload and Meta Data of the actual deadline being used. In the former case, the
     * given {@code deadlineInfo} will be wrapped as the payload of a DeadlineMessage.
     * </p>
     *
     * @param triggerDuration The amount of time to wait before the deadline handling
     * @param deadlineContext The context in which deadline is scheduled - saga or aggregate, id, and target type
     * @param deadlineInfo    Details about the deadline
     * @param <T>             The type of the deadline details
     * @return the token to use when cancelling the schedule
     *
     * @see DeadlineContext
     */
    default <T> ScheduleToken schedule(Duration triggerDuration, DeadlineContext deadlineContext, T deadlineInfo) {
        ScheduleToken scheduleToken = generateToken();
        schedule(triggerDuration, deadlineContext, deadlineInfo, scheduleToken);
        return scheduleToken;
    }

    /**
     * Schedules a deadline at given {@code triggerDateTime} with provided context. The provided ScheduleToken can be
     * used to cancel the scheduled deadline.
     * <p>
     * The given {@code deadlineInfo} may be any object, as well as a DeadlineMessage. In the latter case, the instance
     * provided is the donor for the payload and Meta Data of the actual deadline being used. In the former case, the
     * given {@code deadlineInfo} will be wrapped as the payload of a DeadlineMessage.
     * </p>
     *
     * @param triggerDateTime The moment to trigger the deadline handling
     * @param deadlineContext The context in which deadline is scheduled - saga or aggregate, id, and target type
     * @param deadlineInfo    Details about the deadline
     * @param scheduleToken   The token to use when cancelling the schedule. It is recommended to use {@link
     *                        #generateToken()} in order to generate this token.
     * @param <T>             The type of the deadline details
     * @throws IllegalArgumentException if ScheduleToken is not compatible with this DeadlineManager
     */
    <T> void schedule(Instant triggerDateTime, DeadlineContext deadlineContext, T deadlineInfo,
                      ScheduleToken scheduleToken);

    /**
     * Schedules a deadline after the given {@code triggerDuration} with provided context. The provided ScheduleToken
     * can be used to cancel the scheduled deadline.
     * <p>
     * The given {@code deadlineInfo} may be any object, as well as a DeadlineMessage. In the latter case, the instance
     * provided is the donor for the payload and Meta Data of the actual deadline being used. In the former case, the
     * given {@code deadlineInfo} will be wrapped as the payload of a DeadlineMessage.
     * </p>
     *
     * @param triggerDuration The amount of time to wait before the deadline handling
     * @param deadlineContext The context in which deadline is scheduled - saga or aggregate, id, and target type
     * @param deadlineInfo    Details about the deadline
     * @param scheduleToken   The token to use when cancelling the schedule. It is recommended to use {@link
     *                        #generateToken()} in order to generate this token.
     * @param <T>             The type of the deadline details
     * @throws IllegalArgumentException if ScheduleToken is not compatible with this DeadlineManager
     */
    <T> void schedule(Duration triggerDuration, DeadlineContext deadlineContext, T deadlineInfo,
                      ScheduleToken scheduleToken);

    /**
     * Generates a schedule token. It is recommended to generate a token with this method when methods with external
     * tokens are used.
     *
     * @return the schedule token
     */
    ScheduleToken generateToken();

    /**
     * Cancels the deadline. If the deadline is already handled, this method does nothing.
     *
     * @param scheduleToken The token used to schedule a deadline
     * @throws IllegalArgumentException if the token belongs to another scheduler
     */
    void cancelSchedule(ScheduleToken scheduleToken);
}
