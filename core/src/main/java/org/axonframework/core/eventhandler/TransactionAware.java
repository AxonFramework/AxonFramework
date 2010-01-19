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
 * Extension on the {@link org.axonframework.core.eventhandler.EventListener} interface that provides implementations the
 * ability to do work at the start and end of a transaction.
 * <p/>
 * Typically, this will involve opening database transactions or connecting to external systems.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public interface TransactionAware {

    /**
     * Invoked by the EventProcessingScheduler before processing a series of events. The given {@link
     * org.axonframework.core.eventhandler.TransactionStatus} may be used to set the maximum batch size for the current
     * transaction.
     *
     * @param transactionStatus The current status of the transaction
     * @see #afterTransaction(TransactionStatus)
     * @see org.axonframework.core.eventhandler.TransactionStatus
     */
    void beforeTransaction(TransactionStatus transactionStatus);

    /**
     * Invoked by the EventProcessingScheduler after a series of events is processed. The given {@link
     * org.axonframework.core.eventhandler.TransactionStatus} may be used to indicate whether the scheduler should yield to
     * other event processing schedulers or not.
     * <p/>
     * This method is always called once for each invocation to {@link #beforeTransaction(TransactionStatus)}, even if
     * no events were processed at all.
     * <p/>
     * Note that this method is called when a transactional batch was handled successfully, as well as when an error
     * occurred. Use the {@link org.axonframework.core.eventhandler.TransactionStatus} object to find information about
     * transaction status and (when failed) the cause of the failure.
     *
     * @param transactionStatus The current status of the transaction
     * @see #beforeTransaction(TransactionStatus)
     * @see org.axonframework.core.eventhandler.TransactionStatus
     */
    void afterTransaction(TransactionStatus transactionStatus);

}
