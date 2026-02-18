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

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.util.ExecutorServiceFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuration for the {@link DistributedQueryBus}.
 * <p>
 * Can be used to modify non-critical settings of the bus, such as the query and query response thread pools.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public final class DistributedQueryBusConfiguration {

    private static final int DEFAULT_QUERY_THREADS = 10;
    private static final int DEFAULT_QUERY_QUEUE_CAPACITY = 1000;
    private static final Function<Integer, ExecutorServiceFactory<DistributedQueryBusConfiguration>> DEFAULT_QUERY_EXECUTOR_SERVICE_FACTORY =
            (threads) -> (config, queue) -> new ThreadPoolExecutor(
                    threads,
                    threads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    queue,
                    new AxonThreadFactory("QueryProcessor")
            );
    @Nonnull private final ExecutorServiceFactory<DistributedQueryBusConfiguration> queryExecutorServiceFactory;
    private final Supplier<BlockingQueue<Runnable>> queue;
    private final boolean preferLocalQueryHandler;

    private DistributedQueryBusConfiguration(
            @Nonnull ExecutorServiceFactory<DistributedQueryBusConfiguration> queryExecutorServiceFactory,
            @Nonnull Supplier<BlockingQueue<Runnable>> queue,
            boolean preferLocalQueryHandler) {
        this.queryExecutorServiceFactory = queryExecutorServiceFactory;
        this.queue = queue;
        this.preferLocalQueryHandler = preferLocalQueryHandler;
    }

    /**
     * Constructs a default {@code DistributedQueryBusConfiguration} with the following settings:
     * <ul>
     *     <li>Query threads: 10</li>
     *     <li>Query queue capacity: 1000</li>
     *     <li>Prefer local query handler: {@code true}</li>
     * </ul>
     */
    public DistributedQueryBusConfiguration() {
        this(DEFAULT_QUERY_EXECUTOR_SERVICE_FACTORY.apply(DEFAULT_QUERY_THREADS),
             () -> new PriorityBlockingQueue<>(DEFAULT_QUERY_QUEUE_CAPACITY),
             true);
    }

    /**
     * Registers an Executor Service that uses a thread pool with the given amount of {@code queryThreads}.
     * <p>
     * Defaults to a pool with 10 threads.
     *
     * @param queryThreads the number of threads to use for the distributed query bus
     * @return the configuration itself, for fluent API usage
     */
    public DistributedQueryBusConfiguration queryThreads(int queryThreads) {
        return new DistributedQueryBusConfiguration(DEFAULT_QUERY_EXECUTOR_SERVICE_FACTORY.apply(queryThreads),
                                                    queue,
                                                    preferLocalQueryHandler);
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
        return new DistributedQueryBusConfiguration(queryExecutorServiceFactory,
                                                    () -> new PriorityBlockingQueue<>(queryQueueCapacity),
                                                    preferLocalQueryHandler);
    }

    /**
     * Sets the {@link ExecutorService} to use for querying in the distributed query bus.
     * <p>
     * Defaults to a fixed thread pool with 10 threads using the priority queue.
     *
     * @param executorService the {@link ExecutorService} to use for querying in the distributed query bus
     * @return the configuration itself, for fluent API usage
     */
    public DistributedQueryBusConfiguration queryExecutorService(@Nonnull ExecutorService executorService) {
        Objects.requireNonNull(executorService, "The ExecutorService may not be null.");
        return new DistributedQueryBusConfiguration((config, queue) -> executorService,
                                                    queue,
                                                    preferLocalQueryHandler);
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
        return new DistributedQueryBusConfiguration(queryExecutorServiceFactory, queue, preferLocalQueryHandler);
    }

    /**
     * Indicates whether local query handlers are used directly when available, bypassing remote dispatch.
     *
     * @return {@code true} if local handlers are used directly when available, {@code false} if all queries are
     * dispatched through the connector
     */
    public boolean preferLocalQueryHandler() {
        return preferLocalQueryHandler;
    }

    /**
     * Creates and returns the {@link ExecutorService} for query processing using the configured
     * {@link ExecutorServiceFactory} and queue.
     *
     * @return the {@link ExecutorService} for query processing
     */
    public ExecutorService queryExecutorService() {
        return queryExecutorServiceFactory.createExecutorService(this, queue.get());
    }
}