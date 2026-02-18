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

import org.junit.jupiter.api.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DistributedQueryBusConfiguration} verifying configuration methods, default values, and fluent
 * API behavior.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
class DistributedQueryBusConfigurationTest {

    @Test
    void defaultConfigurationHasExpectedValues() {
        // Given / When
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // Then
        assertTrue(config.preferLocalQueryHandler(),
                   "Default configuration should prefer local query handlers");

        ExecutorService executorService = config.queryExecutorService();
        assertInstanceOf(ThreadPoolExecutor.class, executorService,
                        "Default executor should be a ThreadPoolExecutor");

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;
        assertEquals(10, threadPool.getCorePoolSize(),
                    "Default thread pool should have 10 threads");
        assertEquals(10, threadPool.getMaximumPoolSize(),
                    "Default thread pool should have max 10 threads");

        executorService.shutdown();
    }

    @Test
    void queryThreadsCreatesExecutorWithCorrectThreadCount() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // When
        DistributedQueryBusConfiguration customConfig = config.queryThreads(42);

        // Then
        assertNotSame(config, customConfig,
                     "queryThreads() should return a new instance");

        ExecutorService executorService = customConfig.queryExecutorService();
        assertInstanceOf(ThreadPoolExecutor.class, executorService);

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;
        assertEquals(42, threadPool.getCorePoolSize(),
                    "Thread pool should have 42 threads");
        assertEquals(42, threadPool.getMaximumPoolSize(),
                    "Thread pool should have max 42 threads");

        executorService.shutdown();
    }

    @Test
    void queryQueueCapacityCreatesQueueWithCorrectCapacity() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // When
        DistributedQueryBusConfiguration customConfig = config.queryQueueCapacity(500);

        // Then
        assertNotSame(config, customConfig,
                     "queryQueueCapacity() should return a new instance");

        ExecutorService executorService = customConfig.queryExecutorService();
        assertInstanceOf(ThreadPoolExecutor.class, executorService);

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;
        // PriorityBlockingQueue has unbounded capacity, but initial capacity affects internal array
        assertNotNull(threadPool.getQueue(),
                     "Thread pool should have a queue");

        executorService.shutdown();
    }

    @Test
    void queryExecutorServiceUsesProvidedExecutor() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();
        ExecutorService customExecutor = Executors.newSingleThreadExecutor();

        // When
        DistributedQueryBusConfiguration customConfig = config.queryExecutorService(customExecutor);

        // Then
        assertNotSame(config, customConfig,
                     "queryExecutorService() should return a new instance");
        assertSame(customExecutor, customConfig.queryExecutorService(),
                  "Configuration should use the provided executor service");

        customExecutor.shutdown();
    }

    @Test
    void queryExecutorServiceRejectsNullExecutor() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // When / Then
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                    () -> config.queryExecutorService(null),
                    "Setting null executor should throw NullPointerException");
    }

    @Test
    void preferLocalQueryHandlerStoresCorrectValue() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // When
        DistributedQueryBusConfiguration disabledConfig = config.preferLocalQueryHandler(false);
        DistributedQueryBusConfiguration enabledConfig = config.preferLocalQueryHandler(true);

        // Then
        assertNotSame(config, disabledConfig,
                     "preferLocalQueryHandler() should return a new instance");
        assertNotSame(config, enabledConfig,
                     "preferLocalQueryHandler() should return a new instance");

        assertTrue(config.preferLocalQueryHandler(),
                  "Original config should have default value (true)");
        assertFalse(disabledConfig.preferLocalQueryHandler(),
                   "Disabled config should have local shortcut disabled");
        assertTrue(enabledConfig.preferLocalQueryHandler(),
                  "Enabled config should have local shortcut enabled");
    }

    @Test
    void fluentChainingPreservesAllSettings() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // When
        DistributedQueryBusConfiguration customConfig = config
                .queryThreads(20)
                .preferLocalQueryHandler(false)
                .queryQueueCapacity(2000);

        // Then
        assertFalse(customConfig.preferLocalQueryHandler(),
                   "Chained config should preserve preferLocalQueryHandler setting");

        ExecutorService executorService = customConfig.queryExecutorService();
        assertInstanceOf(ThreadPoolExecutor.class, executorService);

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;
        assertEquals(20, threadPool.getCorePoolSize(),
                    "Chained config should preserve thread count setting");

        executorService.shutdown();
    }

    @Test
    void configurationIsImmutable() {
        // Given
        DistributedQueryBusConfiguration original = new DistributedQueryBusConfiguration();

        // When
        DistributedQueryBusConfiguration modified1 = original.queryThreads(5);
        DistributedQueryBusConfiguration modified2 = original.preferLocalQueryHandler(false);

        // Then
        assertNotSame(original, modified1,
                     "Modifying configuration should return new instance");
        assertNotSame(original, modified2,
                     "Modifying configuration should return new instance");
        assertNotSame(modified1, modified2,
                     "Each modification should return distinct instance");

        // Verify original is unchanged
        assertTrue(original.preferLocalQueryHandler(),
                  "Original config should be unchanged");

        ExecutorService originalExecutor = original.queryExecutorService();
        assertInstanceOf(ThreadPoolExecutor.class, originalExecutor);
        assertEquals(10, ((ThreadPoolExecutor) originalExecutor).getCorePoolSize(),
                    "Original config should have default thread count");

        originalExecutor.shutdown();
        modified1.queryExecutorService().shutdown();
        modified2.queryExecutorService().shutdown();
    }

    @Test
    void multipleCallsToQueryExecutorServiceReturnDifferentInstances() {
        // Given
        DistributedQueryBusConfiguration config = new DistributedQueryBusConfiguration();

        // When
        ExecutorService executor1 = config.queryExecutorService();
        ExecutorService executor2 = config.queryExecutorService();

        // Then
        assertNotSame(executor1, executor2,
                     "Each call to queryExecutorService() should create a new executor");

        executor1.shutdown();
        executor2.shutdown();
    }
}
