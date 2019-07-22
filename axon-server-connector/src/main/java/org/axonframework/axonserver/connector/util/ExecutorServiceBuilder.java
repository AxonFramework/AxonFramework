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
 * configuration of the used ExecutorService in for example the
 * {@link org.axonframework.axonserver.connector.command.AxonServerCommandBus}, but maintaining the option for the
 * framework to provide a BlockingQueue which is tailored towards message prioritization when building the executor.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
@FunctionalInterface
public interface ExecutorServiceBuilder extends
        BiFunction<AxonServerConfiguration, BlockingQueue<Runnable>, ExecutorService> {

    long THREAD_KEEP_ALIVE_TIME = 100L;

    /**
     * Create a default ExecutorServiceBuilder used to create a {@link ThreadPoolExecutor} for processing incoming
     * commands. Uses the {@link AxonServerConfiguration#getCommandThreads()} as the core and maximum pool size, a
     * keep-alive time of {@code 100ms}, the given {@link BlockingQueue} as the {@code workQueue} and an
     * {@link AxonThreadFactory}.
     *
     * @return a default ExecutorServiceBuilder to create an executor for processing commands
     */
    static ExecutorServiceBuilder defaultCommandExecutorServiceBuilder() {
        return (configuration, commandProcessQueue) -> new ThreadPoolExecutor(
                configuration.getCommandThreads(),
                configuration.getCommandThreads(),
                THREAD_KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                commandProcessQueue,
                new AxonThreadFactory("CommandProcessor")
        );
    }

    /**
     * Create a default ExecutorServiceBuilder used to create a {@link ThreadPoolExecutor} for processing incoming
     * queries. Uses the {@link AxonServerConfiguration#getQueryThreads()} as the core and maximum pool size, a
     * keep-alive time of {@code 100ms}, the given {@link BlockingQueue} as the {@code workQueue} and an
     * {@link AxonThreadFactory}.
     *
     * @return a default ExecutorServiceBuilder to create an executor for processing queries
     */
    static ExecutorServiceBuilder defaultQueryExecutorServiceBuilder() {
        return (configuration, queryProcessQueue) -> new ThreadPoolExecutor(
                configuration.getQueryThreads(),
                configuration.getQueryThreads(),
                THREAD_KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                queryProcessQueue,
                new AxonThreadFactory("QueryProcessor")
        );
    }
}
