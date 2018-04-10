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
import java.util.function.BiFunction;

/**
 * Throttle backpressure mechanism - the {@code delegateUpdateHandler} will be invoked after given period of time.
 * Update value is output of provided reduction function which takes first and last update within given period of time
 * and returns a single update. If there were no updates within given period, {@code delegateUpdateHandler} will not be
 * invoked. Do note that invocation of {@code delegateUpdateHandler} will be done in separate (worker) thread.
 * <p>
 * Deliberate choice has to be made whether losing some updates is fine by the specific use case.
 *
 * @param <I> type of initial result
 * @param <U> type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public class ThrottleBackpressure<I, U> extends TimeBasedBackpressure<I, U> {

    private final AtomicReference<U> firstUpdateRef = new AtomicReference<>();
    private final AtomicReference<U> lastUpdateRef = new AtomicReference<>();

    /**
     * Initializes {@link ThrottleBackpressure} with delegateUpdateHandler update handler and parameters for
     * scheduling.
     *
     * @param delegateUpdateHandler the delegateUpdateHandler update handler
     * @param initialDelay          the delay after which to start scheduling of updates
     * @param period                the period on which to schedule updates
     * @param unit                  time unit
     * @param reductionFunction     the function which will take first and last update and reduce them to single
     *                              update
     */
    public ThrottleBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long initialDelay, long period,
                                TimeUnit unit, BiFunction<U, U, U> reductionFunction) {
        this(delegateUpdateHandler,
             initialDelay,
             period,
             unit,
             reductionFunction,
             Executors.newSingleThreadScheduledExecutor());
    }

    /**
     * Initializes {@link ThrottleBackpressure} with delegateUpdateHandler update handler and parameters for
     * scheduling.
     *
     * @param delegateUpdateHandler    the delegateUpdateHandler update handler
     * @param initialDelay             the delay after which to start scheduling of updates
     * @param period                   the period on which to schedule updates
     * @param unit                     time unit
     * @param reductionFunction        the function which will take first and last update and reduce them to single
     *                                 update
     * @param scheduledExecutorService scheduled executor service
     */
    public ThrottleBackpressure(UpdateHandler<I, U> delegateUpdateHandler, long initialDelay, long period,
                                TimeUnit unit, BiFunction<U, U, U> reductionFunction,
                                ScheduledExecutorService scheduledExecutorService) {
        super(delegateUpdateHandler, period, unit, scheduledExecutorService);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            U firstUpdate = firstUpdateRef.getAndSet(null);
            U lastUpdate = lastUpdateRef.getAndSet(null);
            if (firstUpdate != null && lastUpdate != null) {
                delegateUpdateHandler.onUpdate(reductionFunction.apply(firstUpdate, lastUpdate));
            }
        }, initialDelay, period, unit);
    }

    @Override
    public void onUpdate(U update) {
        firstUpdateRef.compareAndSet(null, update);
        lastUpdateRef.set(update);
    }
}
