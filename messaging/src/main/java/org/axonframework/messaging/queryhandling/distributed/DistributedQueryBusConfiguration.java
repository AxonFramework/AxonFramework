/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.util.ExecutorServiceFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * Configuration for the {@link DistributedQueryBus}.
 * <p>
 * Can be used to modify non-critical settings of the bus, such as the query and query response thread pools.
 *
 * @param queryThreads            The number of threads used by the {@link DistributedQueryBus}.
 * @param queryQueueCapacity      The initial capacity of the priority queue used for query processing tasks.
 * @param executorServiceFactory   The {@link ExecutorServiceFactory} constructing the priority-aware
 *                                {@link ExecutorService} for the {@link DistributedQueryBus}.
 * @param preferLocalQueryHandler Whether to use local query handlers directly when available, bypassing remote
 *                                dispatch.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @since 5.0.0
 */
public record DistributedQueryBusConfiguration(
        int queryThreads,
        int queryQueueCapacity,
        ExecutorServiceFactory<DistributedQueryBusConfiguration> executorServiceFactory,
        boolean preferLocalQueryHandler
) {

    private static final int DEFAULT_QUERY_THREADS = 10;
    private static final int DEFAULT_QUERY_QUEUE_CAPACITY = 1000;
    private static final ExecutorServiceFactory<DistributedQueryBusConfiguration> DEFAULT_EXECUTOR_SERVICE_FACTORY =
            (configuration, queue) -> new ThreadPoolExecutor(
                    configuration.queryThreads(),
                    configuration.queryThreads(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    queue,
                    new AxonThreadFactory("QueryProcessor")
            );

    /**
     * Compact constructor validating that the given {@code queryThreads} and {@code queryQueueCapacity} are strictly
     * positive.
     */
    @SuppressWarnings("MissingJavadoc")
    public DistributedQueryBusConfiguration {
        assertStrictPositive(queryThreads, "Number of query threads must be greater than 0.");
        assertStrictPositive(queryQueueCapacity, "Query queue capacity must be greater than 0.");
    }

    /**
     * A default instance of the {@link DistributedQueryBusConfiguration}, setting the {@link #queryThreads()} to 10,
     * the {@link #queryQueueCapacity()} to 1000, the {@link #executorServiceFactory()} to a priority-aware
     * {@link ExecutorServiceFactory} using the configured number of threads, and {@link #preferLocalQueryHandler()} to
     * {@code true}.
     */
    public static final DistributedQueryBusConfiguration DEFAULT = new DistributedQueryBusConfiguration(
            DEFAULT_QUERY_THREADS, DEFAULT_QUERY_QUEUE_CAPACITY, DEFAULT_EXECUTOR_SERVICE_FACTORY, true
    );

    /**
     * Sets the number of threads to use for the distributed query bus.
     * <p>
     * Defaults to 10.
     *
     * @param queryThreads the number of threads to use for the distributed query bus
     * @return the configuration itself, for fluent API usage
     */
    public DistributedQueryBusConfiguration queryThreads(int queryThreads) {
        return new DistributedQueryBusConfiguration(
                queryThreads, queryQueueCapacity, DEFAULT_EXECUTOR_SERVICE_FACTORY, preferLocalQueryHandler
        );
    }

    /**
     * Sets the capacity of the priority queue used for query processing tasks.
     * <p>
     * Defaults to 1000.
     *
     * @param queryQueueCapacity the capacity of the query processing queue
     * @return the configuration itself, for fluent API usage
     */
    public DistributedQueryBusConfiguration queryQueueCapacity(int queryQueueCapacity) {
        return new DistributedQueryBusConfiguration(
                queryThreads, queryQueueCapacity, executorServiceFactory, preferLocalQueryHandler
        );
    }

    /**
     * Sets the {@link ExecutorService} to use for querying in the distributed query bus.
     * <p>
     * Defaults to a fixed thread pool with 10 threads using the priority queue.
     *
     * @param executorService the {@link ExecutorService} to use for querying in the distributed query bus
     * @return the configuration itself, for fluent API usage
     */
    public DistributedQueryBusConfiguration queryExecutorService(ExecutorService executorService) {
        Objects.requireNonNull(executorService, "The ExecutorService may not be null.");
        return new DistributedQueryBusConfiguration(
                queryThreads, queryQueueCapacity, (config, queue) -> executorService, preferLocalQueryHandler
        );
    }

    /**
     * Configures whether the distributed query bus should use local query handlers directly when available, bypassing
     * remote dispatch.
     * <p>
     * When enabled, queries for which a local handler is registered will be executed locally without going through
     * the {@link QueryBusConnector}, improving performance by avoiding network overhead. Only when no local handler
     * is available will the query be dispatched remotely. This is safe when query handlers are deterministic and
     * consistent across all nodes.
     * <p>
     * Defaults to {@code true}.
     *
     * @param preferLocalQueryHandler {@code true} to use local handlers directly when available, {@code false} to
     *                                always dispatch through the connector
     * @return the updated instance of {@code DistributedQueryBusConfiguration}, allowing for fluent API usage
     */
    public DistributedQueryBusConfiguration preferLocalQueryHandler(boolean preferLocalQueryHandler) {
        return new DistributedQueryBusConfiguration(
                queryThreads, queryQueueCapacity, executorServiceFactory, preferLocalQueryHandler
        );
    }

    /**
     * Creates and returns the {@link ExecutorService} for query processing using the configured
     * {@link ExecutorServiceFactory} and queue.
     *
     * @return the {@link ExecutorService} for query processing
     */
    public ExecutorService queryExecutorService() {
        return executorServiceFactory.createExecutorService(this, new PriorityBlockingQueue<>(queryQueueCapacity));
    }
}