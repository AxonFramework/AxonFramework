/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.util.ExecutorServiceFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for the {@link DistributedCommandBus}. Can be used to modify non-critical settings of the bus, such as
 * the load factor and thread pool.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class DistributedCommandBusConfiguration {

    private int loadFactor = 100;
    private int numberOfThreads = 10;
    private ExecutorServiceFactory<DistributedCommandBusConfiguration> executorServiceFactory =
            (configuration, commandProcessQueue) -> new ThreadPoolExecutor(
                    configuration.numberOfThreads(),
                    configuration.numberOfThreads(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    commandProcessQueue,
                    new AxonThreadFactory("Command")
            );

    /**
     * Sets the load factor for the distributed command bus. The load factor determines how many commands are sent to each
     * application that is part of the distributed command bus. A higher load factor means that more commands are sent
     * to this node compared to others. Defaults to 100.
     *
     * @param loadFactor The load factor to use for the distributed command bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedCommandBusConfiguration withLoadFactor(int loadFactor) {
        if (loadFactor <= 0) {
            throw new IllegalArgumentException("Load factor must be greater than 0");
        }
        this.loadFactor = loadFactor;
        return this;
    }

    /**
     * Returns the load factor for the distributed command bus.
     *
     * @return The load factor for the distributed command bus.
     */
    public int loadFactor() {
        return loadFactor;
    }

    /**
     * Sets the {@link ExecutorService} to use for the distributed command bus. Defaults to a fixed thread pool with 10
     * threads.
     *
     * @param executorService The {@link ExecutorService} to use for the distributed command bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedCommandBusConfiguration withExecutorService(@Nonnull ExecutorService executorService) {
        this.executorServiceFactory = (config, queue) -> executorService;
        return this;
    }

    /**
     * Returns the {@link ExecutorService} used by the distributed command bus.
     *
     * @return The {@link ExecutorService} used by the distributed command bus.
     */
    public ExecutorServiceFactory<DistributedCommandBusConfiguration> executorServiceFactory() {
        return executorServiceFactory;
    }

    /**
     * Sets the number of threads to use for the distributed command bus. Defaults to 10.
     *
     * @param numberOfThreads The number of threads to use for the distributed command bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedCommandBusConfiguration withNumberOfThreads(int numberOfThreads) {
        if (numberOfThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be greater than 0");
        }
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    /**
     * Returns the number of threads used by the distributed command bus.
     *
     * @return The number of threads used by the distributed command bus.
     */
    public int numberOfThreads() {
        return numberOfThreads;
    }
}