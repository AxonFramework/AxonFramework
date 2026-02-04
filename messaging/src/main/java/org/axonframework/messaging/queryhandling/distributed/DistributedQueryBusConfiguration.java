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

package org.axonframework.messaging.queryhandling.distributed;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.util.ExecutorServiceFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * Configuration for the {@link DistributedQueryBus}.
 * <p>
 * Can be used to modify non-critical settings of the bus, such as the query and query response thread pools.
 *
 * @param queryThreads                        The number of threads used by the {@link DistributedQueryBus} for
 *                                            querying.
 * @param queryExecutorServiceFactory         The {@link ExecutorServiceFactory} constructing the priority-aware
 *                                            {@link ExecutorService} for querying in the {@link DistributedQueryBus}.
 * @param queryResponseThreads                The number of threads used by the {@link DistributedQueryBus} for handling
 *                                            query responses.
 * @param queryResponseExecutorServiceFactory The {@link ExecutorServiceFactory} constructing the priority-aware
 *                                            {@link ExecutorService} for handling query responses in the
 *                                            {@link DistributedQueryBus}.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record DistributedQueryBusConfiguration(
        int queryThreads,
        @Nonnull ExecutorServiceFactory<DistributedQueryBusConfiguration> queryExecutorServiceFactory,
        int queryResponseThreads,
        @Nonnull ExecutorServiceFactory<DistributedQueryBusConfiguration> queryResponseExecutorServiceFactory
) {

    private static final int DEFAULT_QUERY_THREADS = 10;
    private static final int DEFAULT_RESPONSE_THREADS = 5;
    private static final ExecutorServiceFactory<DistributedQueryBusConfiguration> DEFAULT_QUERY_EXECUTOR_SERVICE_FACTORY =
            (configuration, queryProcessQueue) -> new ThreadPoolExecutor(
                    configuration.queryThreads(),
                    configuration.queryThreads(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    queryProcessQueue,
                    new AxonThreadFactory("QueryProcessor")
            );
    private static final ExecutorServiceFactory<DistributedQueryBusConfiguration> DEFAULT_RESPONSE_EXECUTOR_SERVICE_FACTORY =
            (configuration, queryProcessQueue) -> new ThreadPoolExecutor(
                    configuration.queryThreads(),
                    configuration.queryThreads(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    queryProcessQueue,
                    new AxonThreadFactory("QueryResponseProcessor")
            );


    /**
     * Compact constructor validating that the given {@code queryThreads} and {@code queryResponseThreads} are strictly
     * positive.
     */
    public DistributedQueryBusConfiguration {
        assertStrictPositive(queryThreads, "Number of query threads must be greater than 0.");
        assertStrictPositive(queryResponseThreads, "Number of query response handling threads must be greater than 0.");
    }

    /**
     * A default instance of the {@link DistributedQueryBusConfiguration}, setting the {@link #queryThreads()} to 10,
     * the {@link #queryResponseThreads()} to 5, and the {@link #queryExecutorServiceFactory()} and
     * {@link #queryResponseExecutorServiceFactory()} to priority-aware
     * {@link ExecutorServiceFactory ExecutorServiceFactories} using the configured number of threads.
     */
    public static final DistributedQueryBusConfiguration DEFAULT = new DistributedQueryBusConfiguration(
            DEFAULT_QUERY_THREADS, DEFAULT_QUERY_EXECUTOR_SERVICE_FACTORY,
            DEFAULT_RESPONSE_THREADS, DEFAULT_RESPONSE_EXECUTOR_SERVICE_FACTORY
    );

    /**
     * Sets the number of threads to use for querying in the distributed query bus.
     * <p>
     * Defaults to 10.
     *
     * @param queryThreads The number of threads to use for the distributed query bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedQueryBusConfiguration queryThreads(int queryThreads) {
        return new DistributedQueryBusConfiguration(queryThreads,
                                                    queryExecutorServiceFactory,
                                                    queryResponseThreads,
                                                    queryResponseExecutorServiceFactory);
    }

    /**
     * Sets the {@link ExecutorService} to use for querying in the distributed query bus.
     * <p>
     * Defaults to a fixed thread pool with 10 threads using the priority queue.
     *
     * @param executorService The {@link ExecutorService} to use for querying in the distributed query bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedQueryBusConfiguration queryExecutorService(@Nonnull ExecutorService executorService) {
        Objects.requireNonNull(executorService, "The ExecutorService may not be null.");
        return new DistributedQueryBusConfiguration(queryThreads,
                                                    (config, queue) -> executorService,
                                                    queryResponseThreads,
                                                    queryResponseExecutorServiceFactory);
    }

    /**
     * Sets the number of threads to use for handling query responses in the distributed query bus.
     * <p>
     * Defaults to 5.
     *
     * @param queryResponseThreads The number of threads to use for the distributed query bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedQueryBusConfiguration queryResponseThreads(int queryResponseThreads) {
        return new DistributedQueryBusConfiguration(queryThreads,
                                                    queryExecutorServiceFactory,
                                                    queryResponseThreads,
                                                    queryResponseExecutorServiceFactory);
    }

    /**
     * Sets the {@link ExecutorService} to use for handling query responses in the distributed query bus.
     * <p>
     * Defaults to a fixed thread pool with 5 threads using the priority queue.
     *
     * @param executorService The {@link ExecutorService} to use for handling queries in the distributed query bus.
     * @return The configuration itself, for fluent API usage.
     */
    public DistributedQueryBusConfiguration queryResponseExecutorService(@Nonnull ExecutorService executorService) {
        Objects.requireNonNull(executorService, "The ExecutorService may not be null.");
        return new DistributedQueryBusConfiguration(queryThreads,
                                                    queryExecutorServiceFactory,
                                                    queryResponseThreads,
                                                    (config, queue) -> executorService);
    }
}