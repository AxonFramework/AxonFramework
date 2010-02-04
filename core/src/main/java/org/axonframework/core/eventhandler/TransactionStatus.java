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
 * Provides details about the current status of an event handling transaction. This method is typically accessed through
 * the {@link TransactionAware#beforeTransaction(TransactionStatus) beforeTransaction} and {@link
 * TransactionAware#afterTransaction(TransactionStatus) afterTransaction} methods on {@link TransactionAware}, but may
 * also be obtained through the static {@link TransactionStatus#current()} method.
 * <p/>
 * All instance methods in this class are meant to be used in a single thread and are therefore not thread-safe. The
 * static methods are thread-safe.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class TransactionStatus {

    private static ThreadLocal<TransactionStatus> current = new ThreadLocal<TransactionStatus>();

    private YieldPolicy yieldPolicy = YieldPolicy.YIELD_AFTER_TRANSACTION;
    private int eventsProcessedSinceLastYield = 0;
    private int eventsProcessedInTransaction = 0;
    private int maxTransactionSize = 50;
    private Throwable exception;
    private RetryPolicy retryPolicy = RetryPolicy.SKIP_FAILED_EVENT;
    private long retryInterval = 5000;

    /**
     * Initialize a TransactionStatus instance with default settings.
     */
    protected TransactionStatus() {
        // construction limited to this package
    }

    /**
     * Returns the TransactionStatus object related to a transaction running on the current thread. Returns
     * <code>null</code> if no transaction is running on the current thread.
     *
     * @return the currently active TransactionStatus, or <code>null</code> if none is present.
     */
    public static TransactionStatus current() {
        return current.get();
    }

    /**
     * Clears the TransactionStatus related to the current thread.
     */
    protected static void clear() {
        current.remove();
    }

    /**
     * Sets the TransactionStatus object related to the transaction running in the current thread. If a previous value
     * exists, it is overwritten.
     *
     * @param newStatus The TransactionStatus for the current transaction
     */
    protected static void set(TransactionStatus newStatus) {
        current.set(newStatus);
    }

    /**
     * Returns the number of events processed (so far) in the current transaction.
     *
     * @return the number of events processed (so far) in the current transaction.
     */
    public int getEventsProcessedInTransaction() {
        return eventsProcessedInTransaction;
    }

    /**
     * Returns the number of events processed (so far) since the scheduler last yielded to other threads. If the
     * scheduler never yielded, it indicates the total number of events processed.
     *
     * @return the number of events processed (so far) since the scheduler last yielded
     */
    public int getEventsProcessedSinceLastYield() {
        return eventsProcessedSinceLastYield;
    }

    /**
     * Sets the YieldPolicy for the current transaction. Defaults to {@link YieldPolicy#YIELD_AFTER_TRANSACTION
     * YIELD_AFTER_TRANSACTION}.
     *
     * @param yieldPolicy The YieldPolicy to use for the current transaction
     */
    public void setYieldPolicy(YieldPolicy yieldPolicy) {
        this.yieldPolicy = yieldPolicy;
    }

    /**
     * Returns the YieldPolicy applicable to the current transaction.
     *
     * @return the YieldPolicy applicable to the current transaction
     */
    public YieldPolicy getYieldPolicy() {
        return yieldPolicy;
    }

    /**
     * Forces the EventProcessingScheduler to immediately yield to other schedulers after processing this event. The
     * current transaction will be closed normally.
     */
    public void requestImmediateYield() {
        requestImmediateCommit();
        setYieldPolicy(YieldPolicy.YIELD_AFTER_TRANSACTION);
    }

    /**
     * Requests the EventProcessingScheduler to commit the transaction immediately. Note that if this method is called
     * before any events have been processed, the transaction will close without processing any events.
     */
    public void requestImmediateCommit() {
        maxTransactionSize = eventsProcessedInTransaction;
    }

    /**
     * Returns the maximum number of events that may be processed inside the current transaction.
     *
     * @return the maximum number of events in the current transaction
     */
    public int getMaxTransactionSize() {
        return maxTransactionSize;
    }

    /**
     * Sets the maximum number of events to process inside the current transaction. The scheduler will commit a
     * transaction if this number (or more) events have been processed inside the current transaction.
     * <p/>
     * Defaults to the number of events in the queue at the moment the transaction started.
     *
     * @param maxTransactionSize The number of events to process in the current transaction
     */
    public void setMaxTransactionSize(int maxTransactionSize) {
        this.maxTransactionSize = maxTransactionSize;
    }

    /**
     * Sets the retry policy for the current transaction. Is set to {@link RetryPolicy#SKIP_FAILED_EVENT
     * SKIP_FAILED_EVENT} by default.
     * <p/>
     * Typically, exceptions are caused by programming errors or the underlying processing environment that the event
     * handler uses, such as a database. In the former, there is no point in retrying the event processing. It would
     * cause an unlimited loop in event processing, making an application vulnerable to a poisonous message attack. In
     * the latter case, the event listener should identify which exception is transitive (i.e. might make a chance when
     * retried), and which is not. If an exception is transitive, either {@link RetryPolicy#RETRY_TRANSACTION
     * RETRY_TRANSACTION} or {@link RetryPolicy#RETRY_LAST_EVENT RETRY_LAST_EVENT} should be chosen.
     * <p/>
     * Furthermore, the policy choice should be based on the effect of a transaction rollback. Some data sources, such
     * as databases, roll back the entire transaction. In that case, choose {@link RetryPolicy#RETRY_TRANSACTION
     * RETRY_TRANSACTION} policy. If a rollback on the underlying data source only rolls back the last modification,
     * choose {@link RetryPolicy#RETRY_LAST_EVENT RETRY_LAST_EVENT}.
     * <p/>
     * If failed events should be ignored altogether, choose the {@link RetryPolicy#SKIP_FAILED_EVENT SKIP_FAILED_EVENT}
     * policy.
     * <p/>
     * These policies may be set in both the <code>beforeTransaction()</code> and <code>afterTransaction</code> methods.
     * The latter would allow you to change policy based on the exact type of exception encountered.
     *
     * @param retryPolicy the retry policy to apply when a transaction fails.
     */
    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * Returns the retry policy for the current transaction
     *
     * @return the retry policy for the current transaction
     *
     * @see #setRetryPolicy(RetryPolicy)
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Record the fact that an event has been processed. This will increase the number of events processed in current
     * transaction as well as the number of events since last yield.
     */
    protected void recordEventProcessed() {
        eventsProcessedSinceLastYield++;
        eventsProcessedInTransaction++;
    }

    /**
     * Resets the event count for current transaction to 0 and sets the YieldPolicy to the default value
     * (YIELD_AFTER_TRANSACTION).
     */
    protected void resetTransactionStatus() {
        eventsProcessedInTransaction = 0;
        yieldPolicy = YieldPolicy.YIELD_AFTER_TRANSACTION;
        exception = null;
    }

    /**
     * Indicates whether or not the maximum amount of events have been processed in this transaction.
     *
     * @return true if the maximum amount of events was handled, otherwise false.
     */
    protected boolean isTransactionSizeReached() {
        return eventsProcessedInTransaction >= maxTransactionSize;
    }

    /**
     * Returns the current retry interval. This is the number of milliseconds processing should wait before retrying
     * this transaction. Defaults to 5000 milliseconds.
     * <p/>
     * Note that negative values will result in immediate retries, making them practically equal to a value of 0.
     *
     * @return the current retry interval
     */
    public long getRetryInterval() {
        return retryInterval;
    }

    /**
     * Sets the retry interval for the current transaction. his is the number of milliseconds processing should wait
     * before retrying this transaction. Defaults to 5000 milliseconds.
     *
     * @param retryInterval the number of milliseconds to wait before retrying the transaction
     */
    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    /**
     * Indicates whether the current transactional batch is executed successfully. If a batch is currently in progress,
     * this will indicate if an error has been discovered so far.
     *
     * @return whether the current transaction is successful or not.
     */
    public boolean isSuccessful() {
        return exception == null;
    }

    /**
     * Returns the exception that caused the transaction to be marked as failed. Returns null if transaction is
     * successful. Use {@link #isSuccessful()} to find out if transaction was successful or not.
     *
     * @return the exception that caused the transaction to fail
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Mark the current transaction as failed.
     *
     * @param cause the exception that caused the transaction to fail
     */
    protected void markFailed(Throwable cause) {
        this.exception = cause;
    }
}
