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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Debounce (Throttle With Timeout) backpressure mechanism - the {@code delegateUpdateHandler} {@link UpdateHandler}
 * will be invoked with the last received update after which given timeout passed and there were no updates. Do note
 * that invocation of {@code delegateUpdateHandler} will be done in separate (worker) thread.
 * <p>
 * Deliberate choice has to be made whether losing some updates is fine by the specific use case.
 *
 * @param <I> type of initial result
 * @param <U> type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public class DebounceBackpressure<I, U> extends TimeBasedBackpressure<I, U> {

    private static final boolean INTERRUPT_RUNNING_UPDATE = false;

    private U lastUpdate;
    private ScheduledFuture<?> schedule;
    private final Object scheduleLock = new Object();

    /**
     * Initializes {@link DebounceBackpressure} with delegateUpdateHandler update handler and parameters for scheduling.
     *
     * @param delegateUpdateHandler the delegateUpdateHandler update handler
     * @param period                the period on which to schedule updates
     * @param unit                  time unit
     */
    public DebounceBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long period, TimeUnit unit) {
        this(delegateUpdateHandler, period, unit, Executors.newSingleThreadScheduledExecutor());
    }

    /**
     * Initializes {@link DebounceBackpressure} with delegateUpdateHandler update handler and parameters for scheduling.
     *
     * @param delegateUpdateHandler    the delegateUpdateHandler update handler
     * @param period                   the period on which to schedule updates
     * @param unit                     time unit
     * @param scheduledExecutorService scheduled executor service
     */
    public DebounceBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long period, TimeUnit unit,
                                ScheduledExecutorService scheduledExecutorService) {
        super(delegateUpdateHandler, period, unit, scheduledExecutorService);
    }

    @Override
    public void onUpdate(U update) {
        synchronized (scheduleLock) {
            lastUpdate = update;
            if (schedule != null) {
                schedule.cancel(INTERRUPT_RUNNING_UPDATE);
            }
            schedule = getScheduledExecutorService().schedule(() -> getDelegateUpdateHandler().onUpdate(lastUpdate),
                                                              getPeriod(),
                                                              getUnit());
        }
    }
}
