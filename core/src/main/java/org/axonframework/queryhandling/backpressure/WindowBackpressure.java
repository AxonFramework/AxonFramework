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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Window backpressure mechanism - accumulates updates within given time frame. After time frame passes, {@code
 * original} {@link UpdateHandler} will be invoked. Before this invocation reduction function will be called in order to
 * reduce accumulated updates to a single update. If there are no accumulated updates, {@code original} will not be
 * invoked.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class WindowBackpressure<I, U> extends TimeBasedBackpressure<I, U> {

    private final List<U> buffer;

    /**
     * Initializes {@link WindowBackpressure} with original update handler and parameters for scheduling.
     *
     * @param original          the original update handler
     * @param reductionFunction the function which will take a buffered updates and reduce them to single update
     * @param initialDelay      the delay after which to start scheduling of updates
     * @param period            the period on which to schedule updates
     * @param unit              time unit
     */
    public WindowBackpressure(UpdateHandler<I, U> original, Function<List<U>, U> reductionFunction, long initialDelay,
                              long period, TimeUnit unit) {
        this(original, reductionFunction, initialDelay, period, unit, Executors.newSingleThreadScheduledExecutor());
    }

    /**
     * Initializes {@link WindowBackpressure} with original update handler and parameters for scheduling.
     *
     * @param original                 the original update handler
     * @param reductionFunction        the function which will take a buffered updates and reduce them to single update
     * @param initialDelay             the delay after which to start scheduling of updates
     * @param period                   the period on which to schedule updates
     * @param unit                     time unit
     * @param scheduledExecutorService scheduled executor service
     */
    public WindowBackpressure(UpdateHandler<I, U> original, Function<List<U>, U> reductionFunction, long initialDelay,
                              long period, TimeUnit unit, ScheduledExecutorService scheduledExecutorService) {
        super(original, period, unit, scheduledExecutorService);
        buffer = new ArrayList<>();
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (buffer) {
                if (buffer.size() > 0) {
                    original.onUpdate(reductionFunction.apply(new ArrayList<>(buffer)));
                    buffer.clear();
                }
            }
        }, initialDelay, period, unit);
    }

    @Override
    public void onUpdate(U update) {
        synchronized (buffer) {
            buffer.add(update);
        }
    }
}
