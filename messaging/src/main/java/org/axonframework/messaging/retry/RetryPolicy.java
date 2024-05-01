/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.retry;

import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.Message;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Describes a policy for retrying failed messages. These could be commands, events, as well as queries where the
 * dispatching, or -in the case of commands and queries- the handling returned a failure.
 */
public interface RetryPolicy extends DescribableComponent {

    /**
     * Provides the outcome for the policy given the failed {@code message} that was dispatched and resulted in given
     * {@code failure}. The {@code previousFailures} represent a summary of previous failures that have occurred in
     * previous attemps to send this message.
     *
     * @param message          The message that failed
     * @param failure          The exception that occurred while dispatching
     * @param previousFailures a summary of all previous failures
     * @return the outcome describing the expected rescheduling behavior
     */
    RetryPolicy.Outcome defineFor(@Nonnull Message<?> message, @Nonnull Throwable failure,
                                  @Nonnull List<Class<? extends Throwable>[]> previousFailures);

    /**
     * The outcome of applying a {@link RetryPolicy} to a given message. The outcome can either be
     * {@link #rescheduleIn(long, TimeUnit) to reschedule} or to {@link  #doNotReschedule() not reschedule}.
     */
    sealed interface Outcome permits RetryOutcome, DoNotRetryOutcome {

        /**
         * Factory method to create an Outcome that requests the retry to be scheduled at the given {@code interval}
         * (using given {@code timeUnit}).
         *
         * @param interval The amount of time to wait before rescheduling the message
         * @param timeUnit The unit of time in which interval is expressed
         * @return an Outcome representing a request to reschedule
         */
        static Outcome rescheduleIn(long interval, TimeUnit timeUnit) {
            return new RetryOutcome(interval, timeUnit);
        }

        /**
         * Factory method to create an Outcome that requests the retry to not be rescheduled. Any failure will be
         * relayed to the caller.
         *
         * @return an Outcome that requests the retry to not be rescheduled
         */
        static Outcome doNotReschedule() {
            return new DoNotRetryOutcome();
        }

        /**
         * Indicates whether this Outcome indicates rescheduling the dispatch of a message.
         *
         * @return {@code true} if the message should be rescheduled, otherwise {@code false}
         */
        boolean shouldReschedule();

        /**
         * The amount of time to wait before rescheduling the message. Should be ignored when
         * {@link #shouldReschedule()} returns {@code false}.
         *
         * @return amount of time to wait before rescheduling the message
         */
        long rescheduleInterval();

        /**
         * The unit in which the interval is expressed
         *
         * @return the  unit in which the interval is expressed
         */
        TimeUnit rescheduleIntervalTimeUnit();
    }
}
