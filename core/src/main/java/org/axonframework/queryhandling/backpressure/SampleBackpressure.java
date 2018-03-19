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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sample (Throttle Last) backpressure mechanism - the {@code delegateUpdateHandler} {@link UpdateHandler} will be
 * invoked after given period of time with last update received. If there were no updates within given period, {@code
 * delegateUpdateHandler} will not be invoked. Do note that invocation of {@code delegateUpdateHandler} will be done in
 * separate (worker) thread.
 * <p>
 * Deliberate choice has to be made whether losing some updates is fine by the specific use case.
 *
 * @param <I> type of initial result
 * @param <U> type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public class SampleBackpressure<I, U> extends TimeBasedBackpressure<I, U> {

    private final AtomicReference<U> lastUpdate = new AtomicReference<>();

    /**
     * Initializes {@link SampleBackpressure} with delegateUpdateHandler update handler and parameters for scheduling.
     *
     * @param delegateUpdateHandler the delegateUpdateHandler update handler
     * @param initialDelay          the delay after which to start scheduling of updates
     * @param period                the period on which to schedule updates
     * @param unit                  time unit
     */
    public SampleBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long initialDelay, long period,
                              TimeUnit unit) {
        this(delegateUpdateHandler, initialDelay, period, unit, Executors.newSingleThreadScheduledExecutor());
    }

    /**
     * Initializes {@link SampleBackpressure} with delegateUpdateHandler update handler and parameters for scheduling.
     *
     * @param delegateUpdateHandler    the delegateUpdateHandler update handler
     * @param initialDelay             the delay after which to start scheduling of updates
     * @param period                   the period on which to schedule updates
     * @param unit                     time unit
     * @param scheduledExecutorService scheduled executor service
     */
    public SampleBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long initialDelay, long period, TimeUnit unit,
                              ScheduledExecutorService scheduledExecutorService) {
        super(delegateUpdateHandler, period, unit, scheduledExecutorService);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            U current = lastUpdate.getAndSet(null);
            if (current != null) {
                delegateUpdateHandler.onUpdate(current);
            }
        }, initialDelay, period, unit);
    }

    @Override
    public void onUpdate(U update) {
        lastUpdate.set(update);
    }
}
