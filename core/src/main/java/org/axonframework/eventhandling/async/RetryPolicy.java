/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.async;

import java.util.concurrent.TimeUnit;

/**
 * This policy tells the EventProcessor how it should deal with failed transactions. <ul> <li>{@link
 * #proceed()} will tell the scheduler to ignore the failure and continue
 * processing. This policy can be used to indicate sufficient recovery or retrying has been done, or that the error
 * cannot be recovered from, and should be ignored.</li>
 * <li>{@link #skip()} will rollback the Unit of Work and proceed with the next event, effectively skipping the
 * events.</li>
 * <li>{@link #retryAfter(int, java.util.concurrent.TimeUnit)} tells the scheduler to roll back the current
 * Unit of Work and process it again after the given amount of time. All Event Listeners will receive the event
 * again.</li></ul>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class RetryPolicy {

    private static final RetryPolicy PROCEED = new SimpleRetryPolicy(0, false, false);
    private static final RetryPolicy SKIP = new SimpleRetryPolicy(0, true, false);

    /**
     * Tells the scheduler to ignore the failure continue processing. The Event is processed by the remaining Event
     * Listeners in the Cluster.
     *
     * @return A RetryPolicy instance requesting the scheduler to proceed with dispatching
     */
    public static RetryPolicy proceed() {
        return PROCEED;
    }

    /**
     * This policy will roll back the Unit of Work (and the transaction) and skip the event altogether. When all
     * listeners support the type of transaction backed by the Unit of Work, this effectively means that the Event can
     * be regarded as not processed. The Event will not be handled by any remaining Event Listeners in the Cluster.
     *
     * @return A RetryPolicy instance requesting the scheduler to rollback the Unit of Work and continue processing the
     *         next Event.
     */
    public static RetryPolicy skip() {
        return SKIP;
    }

    /**
     * This policy will roll back the Unit of Work (and the transaction), if any) and reschedule the event for
     * processing. The Event will not be handled by any remaining Event Listeners in the Cluster.
     *
     * @param timeout The amount of time to wait before retrying
     * @param unit    The unit of time for the timeout
     * @return a RetryPolicy requesting a rollback and a retry of the failed Event after the given timeout
     */
    public static RetryPolicy retryAfter(int timeout, TimeUnit unit) {
        return new SimpleRetryPolicy(unit.toMillis(timeout), true, true);
    }

    /**
     * Returns the time the scheduler should wait before continuing processing. This value is ignored if {@link
     * #requiresRescheduleEvent()} returns <code>false</code>.
     *
     * @return the amount of time, in milliseconds, the scheduler should wait before continuing processing.
     */
    public abstract long waitTime();

    /**
     * Indicates whether the scheduler should reschedule the failed event.
     *
     * @return <code>true</code> if the scheduler should reschedule the failed event, otherwise <code>false</code>
     */
    public abstract boolean requiresRescheduleEvent();

    /**
     * Indicates whether the scheduler should rollback the Unit of Work wrapping the event handling.
     *
     * @return <code>true</code> to indicate the scheduler should perform a rollback or <code>false</code> to request a
     *         commit.
     */
    public abstract boolean requiresRollback();

    private static final class SimpleRetryPolicy extends RetryPolicy {

        private final long waitTime;
        private final boolean rollback;
        private final boolean rescheduleEvent;

        private SimpleRetryPolicy(long waitTime, boolean rollback, boolean rescheduleEvent) {
            this.waitTime = waitTime;
            this.rollback = rollback;
            this.rescheduleEvent = rescheduleEvent;
        }

        @Override
        public long waitTime() {
            return waitTime;
        }

        @Override
        public boolean requiresRescheduleEvent() {
            return rescheduleEvent;
        }

        @Override
        public boolean requiresRollback() {
            return rollback;
        }
    }
}
