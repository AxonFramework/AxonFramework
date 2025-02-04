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

package org.axonframework.axonserver.connector.util;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.AxonThreadFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * A Functional Interface towards a {@link BiFunction} which ingests both an {@link AxonServerConfiguration} and a
 * {@link BlockingQueue} of {@link Runnable}, and outputs an {@link ExecutorService}. Provides a means to allow
 * configuration of the used ExecutorService in for example the {@link org.axonframework.axonserver.connector.command.AxonServerCommandBus},
 * but maintaining the option for the framework to provide a BlockingQueue which is tailored towards message
 * prioritization when building the executor.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
@FunctionalInterface
public interface ExecutorServiceBuilder extends
        BiFunction<AxonServerConfiguration, BlockingQueue<Runnable>, ExecutorService> {

    /**
     * Create a default ExecutorServiceBuilder used to create a {@link ThreadPoolExecutor} for processing incoming
     * commands. Uses the {@link AxonServerConfiguration#getCommandThreads()} as the core and maximum pool size,
     * the given {@link BlockingQueue} as the {@code workQueue} and an {@link AxonThreadFactory}.
     *
     * @return a default ExecutorServiceBuilder to create an executor for processing commands
     */
    static ExecutorServiceBuilder defaultCommandExecutorServiceBuilder() {
        return (configuration, commandProcessQueue) -> new ThreadPoolExecutor(
                configuration.getCommandThreads(),
                configuration.getCommandThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                commandProcessQueue,
                new AxonThreadFactory("CommandProcessor")
        );
    }

    /**
     * Create a default ExecutorServiceBuilder used to create a {@link ThreadPoolExecutor} for processing incoming
     * queries. Uses the {@link AxonServerConfiguration#getQueryThreads()} as the core and maximum pool size,
     * the given {@link BlockingQueue} as the {@code workQueue} and an {@link AxonThreadFactory}.
     *
     * @return a default ExecutorServiceBuilder to create an executor for processing queries
     */
    static ExecutorServiceBuilder defaultQueryExecutorServiceBuilder() {
        return (configuration, queryProcessQueue) -> new ThreadPoolExecutor(
                configuration.getQueryThreads(),
                configuration.getQueryThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                queryProcessQueue,
                new AxonThreadFactory("QueryProcessor")
        );
    }


    /**
     * Create a default ExecutorServiceBuilder used to create a {@link ThreadPoolExecutor} for processing incoming
     * query responses.
     * Uses the {@link AxonServerConfiguration#getQueryResponseThreads()} as the core and maximum pool size,
     * the given {@link BlockingQueue} as the {@code workQueue} and an {@link AxonThreadFactory}.
     *
     * @return a default ExecutorServiceBuilder to create an executor for processing incoming query responses
     */
    static ExecutorServiceBuilder defaultQueryResponseExecutorServiceBuilder() {
        return (configuration, queryResponseProcessQueue) -> new ThreadPoolExecutor(
                configuration.getQueryResponseThreads(),
                configuration.getQueryResponseThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                queryResponseProcessQueue,
                new AxonThreadFactory("QueryResponse")
        );
    }
}
