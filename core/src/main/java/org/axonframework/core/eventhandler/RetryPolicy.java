/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler;

/**
 * This policy tells the EventProcessingScheduler how it should deal with failed transactions. <ul> <li>{@link
 * #SKIP_FAILED_EVENT} will tell the scheduler to ignore the failure and schedule a new transaction to continue
 * processing. <li>{@link #RETRY_TRANSACTION} tells the scheduler to offer all the events in the failed transaction to
 * the event listener in a new transaction. <li>{@link #RETRY_LAST_EVENT} tell the scheduler to only offer the last
 * event form this transaction to the event listener. This event will be handled in a new transaction. </ul>
 *
 * @author Allard Buijze
 * @since 0.4
 */
public enum RetryPolicy {

    /**
     * Tells the scheduler to ignore the failure and schedule a new transaction to continue processing. Note that any
     * retry interval is ignored, and a new transaction is scheduled for immediate execution when this retry policy is
     * applied.
     */
    SKIP_FAILED_EVENT,

    /**
     * Tells the scheduler to offer all the events in the failed transaction to the event listener in a new
     * transaction.
     */
    RETRY_TRANSACTION,

    /**
     * Tells the scheduler to only offer the last event form this transaction to the event listener. This event will be
     * handled in a new transaction.
     */
    RETRY_LAST_EVENT

}
