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

package org.axonframework.queryhandling.backpressure;

import org.axonframework.queryhandling.UpdateHandler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;

/**
 * Base class for all time based backpressure mechanisms.
 *
 * @param <I> type of initial response
 * @param <U> type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public abstract class TimeBasedBackpressure<I, U> implements BackpressuredUpdateHandler<I, U> {

    private final UpdateHandler<I, U> delegateUpdateHandler;
    private final ScheduledExecutorService scheduledExecutorService;
    private final long period;
    private final TimeUnit unit;

    /**
     * Initializes {@link TimeBasedBackpressure} with delegateUpdateHandler update handler and parameters for
     * scheduling.
     *
     * @param delegateUpdateHandler    the delegateUpdateHandler update handler
     * @param period                   the period on which to schedule updates
     * @param unit                     time unit
     * @param scheduledExecutorService scheduled executor service
     */
    public TimeBasedBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long period, TimeUnit unit,
                                 ScheduledExecutorService scheduledExecutorService) {
        this.delegateUpdateHandler = delegateUpdateHandler;
        this.scheduledExecutorService = scheduledExecutorService;
        this.period = period;
        this.unit = unit;
    }

    @Override
    public void onInitialResult(I initial) {
        delegateUpdateHandler.onInitialResult(initial);
    }

    @Override
    public void onCompleted() {
        shutdown();
    }

    /**
     * Shuts down the executor service, there will be no more updates scheduled.
     */
    @PreDestroy
    public void shutdown() {
        try {
            scheduledExecutorService.shutdown();
            // await for the last update
            scheduledExecutorService.awaitTermination(period, unit);
            delegateUpdateHandler.onCompleted();
        } catch (InterruptedException e) {
            // we've been interrupted. Reset the interruption flag and continue
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onCompletedExceptionally(Throwable error) {
        delegateUpdateHandler.onCompletedExceptionally(error);
    }

    protected UpdateHandler<I, U> getDelegateUpdateHandler() {
        return delegateUpdateHandler;
    }

    protected ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    protected long getPeriod() {
        return period;
    }

    protected TimeUnit getUnit() {
        return unit;
    }
}
