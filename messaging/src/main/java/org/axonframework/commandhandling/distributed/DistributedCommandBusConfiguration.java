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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * Configuration for the {@link DistributedCommandBus}.
 * <p>
 * Can be used to modify non-critical settings of the bus, such as the load factor and thread pool.
 *
 * @param loadFactor             The load factor for the {@link DistributedCommandBus}.
 * @param numberOfThreads        The number of threads used by the {@link DistributedCommandBus}.
 * @param executorServiceFactory The {@link ExecutorServiceFactory} constructing the priority-aware
 *                               {@link ExecutorService} for the {@link DistributedCommandBus}.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record DistributedCommandBusConfiguration(
        int loadFactor,
        int numberOfThreads,
        @Nonnull ExecutorServiceFactory<DistributedCommandBusConfiguration> executorServiceFactory
) {

    private static final int DEFAULT_LOAD_FACTOR = 100;
    private static final int DEFAULT_NUMBER_OF_THREADS = 10;
    private static final ExecutorServiceFactory<DistributedCommandBusConfiguration> DEFAULT_EXECUTOR_SERVICE_FACTORY =
            (configuration, commandProcessQueue) -> new ThreadPoolExecutor(
                    configuration.numberOfThreads(),
                    configuration.numberOfThreads(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    commandProcessQueue,
                    new AxonThreadFactory("Command")
            );

    /**
     * Compact constructor validating that the given {@code loadFactor} and {@code numberOfThreads} are strictly
     * positive.
     */
    @SuppressWarnings("MissingJavadoc")
    public DistributedCommandBusConfiguration {
        assertStrictPositive(loadFactor, "Load factor must be greater than 0");
        assertStrictPositive(numberOfThreads, "Number of threads must be greater than 0");
    }

    /**
     * A default instance of the {@link DistributedCommandBusConfiguration}, setting the {@link #loadFactor()} to 100,
     * the {@link #numberOfThreads()} to 10, and the {@link #executorServiceFactory()} to a priority-aware
     * {@link ExecutorServiceFactory} using the configured number of threads.
     */
    public static final DistributedCommandBusConfiguration DEFAULT = new DistributedCommandBusConfiguration(
            DEFAULT_LOAD_FACTOR, DEFAULT_NUMBER_OF_THREADS, DEFAULT_EXECUTOR_SERVICE_FACTORY);

    /**
     * Sets the load factor for the distributed command bus. The load factor determines how many commands are sent to
     * each application that is part of the distributed command bus. A higher load factor means that more commands are
     * sent to this node compared to others. Defaults to 100.
     *
     * @param loadFactor The load factor to use for the distributed command bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedCommandBusConfiguration loadFactor(int loadFactor) {
        return new DistributedCommandBusConfiguration(loadFactor, numberOfThreads, executorServiceFactory);
    }

    /**
     * Sets the number of threads to use for the distributed command bus. Defaults to 10.
     *
     * @param numberOfThreads The number of threads to use for the distributed command bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedCommandBusConfiguration numberOfThreads(int numberOfThreads) {
        return new DistributedCommandBusConfiguration(loadFactor, numberOfThreads, executorServiceFactory);
    }

    /**
     * Sets the {@link ExecutorService} to use for the distributed command bus. Defaults to a fixed thread pool with 10
     * threads.
     *
     * @param executorService The {@link ExecutorService} to use for the distributed command bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedCommandBusConfiguration executorService(@Nonnull ExecutorService executorService) {
        Objects.requireNonNull(executorService, "The ExecutorService may not be null.");
        return new DistributedCommandBusConfiguration(loadFactor, numberOfThreads, (config, queue) -> executorService);
    }
}