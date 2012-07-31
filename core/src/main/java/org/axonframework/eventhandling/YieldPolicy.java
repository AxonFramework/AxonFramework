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

package org.axonframework.eventhandling;

/**
 * The yielding policy for transactions. This policy tells the EventProcessingScheduler what to do when a transaction is
 * finished.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public enum YieldPolicy {

    /**
     * Continue event processing in the same thread. This option can be used for event listeners that need high-priority
     * event processing. The scheduler will immediately continue processing events in a next transaction.
     */
    DO_NOT_YIELD,

    /**
     * Tells the scheduler to yield to other threads (if possible) after ending the current transaction.
     */
    YIELD_AFTER_TRANSACTION

}
