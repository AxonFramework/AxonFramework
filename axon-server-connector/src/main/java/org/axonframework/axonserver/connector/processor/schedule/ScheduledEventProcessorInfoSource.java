/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.processor.schedule;

import org.axonframework.axonserver.connector.processor.EventProcessorInfoSource;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link EventProcessorInfoSource} that schedule the notification of {@link
 * org.axonframework.eventhandling.EventProcessor}s status.
 *
 * @author Sara Pellegrini
 * @since 4.0
 **/
public class ScheduledEventProcessorInfoSource implements EventProcessorInfoSource {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final int initialDelay;
    private final int schedulingPeriod;
    private final EventProcessorInfoSource delegate;

    /**
     * Initialize the {@link ScheduledEventProcessorInfoSource} using the given {@code initialDelay} and {@code
     * schedulingPeriod}, as milliseconds, to schedule a notification through the {@code delegate} at a fixed rate.
     *
     * @param initialDelay     the initial delay in milliseconds used to notify at a fixed rate
     * @param schedulingPeriod the period in milliseconds after which another notification will be scheduled
     * @param delegate         an {@link EventProcessorInfoSource} used to notify the information at the specific
     *                         interval
     */
    public ScheduledEventProcessorInfoSource(int initialDelay,
                                             int schedulingPeriod,
                                             EventProcessorInfoSource delegate) {
        this.initialDelay = initialDelay;
        this.schedulingPeriod = schedulingPeriod;
        this.delegate = delegate;
    }

    /**
     * Start an {@link java.util.concurrent.Executor} using the given {@code initialDelay} and {@code schedulingPeriod}
     * as milliseconds to notify event processor information. Will be started in phase {@link
     * Phase#INSTRUCTION_COMPONENTS}, to ensure the event processors this source shares information about have been
     * started.
     */
    @StartHandler(phase = Phase.INSTRUCTION_COMPONENTS)
    public void start() {
        scheduler.scheduleAtFixedRate(this::notifyInformation, initialDelay, schedulingPeriod, TimeUnit.MILLISECONDS);
    }

    @Override
    public void notifyInformation() {
        try {
            delegate.notifyInformation();
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * Shuts down the {@link java.util.concurrent.Executor} started through the {@link #start()} method. Will be invoked
     * in phase {@link Phase#INSTRUCTION_COMPONENTS} .
     */
    @ShutdownHandler(phase = Phase.INSTRUCTION_COMPONENTS)
    public void shutdown() {
        scheduler.shutdown();
    }
}
