/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.common.AxonThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

/**
 * Functional interface towards constructing a {@link ScheduledExecutorService} for a {@link PersistentStreamMessageSource}.
 *
 * @author Steven van Beelen
 * @since 4.10.1
 */
@FunctionalInterface
public interface PersistentStreamScheduledExecutorBuilder
        extends BiFunction<Integer, String, ScheduledExecutorService> {

    /**
     * Builds a {@link ScheduledExecutorService} using the given {@code threadCount} and {@code streamName}.
     *
     * @param threadCount The requested thread count for the persistent stream. Can for example be used to define the
     *                    pool size of the {@link ScheduledExecutorService} under construction.
     * @param streamName The name of the persistent stream. Can, for example, be used to define the name of the
     * {@link java.util.concurrent.ThreadFactory} given to a {@link ScheduledExecutorService}
     * @return A {@link ScheduledExecutorService} based on the given {@code threadCount} and {@code streamName}.
     */
    default ScheduledExecutorService build(Integer threadCount, String streamName) {
        return apply(threadCount, streamName);
    }

    /**
     * Default {@link PersistentStreamScheduledExecutorBuilder}. Constructs a {@link ScheduledExecutorService} by using
     * the given {@code threadCount} as the pool size for the executor. Uses the given {@code streamName} to build an
     * {@link AxonThreadFactory} with the group name {@code "PersistentStream[{streamName}]"}.
     *
     * @return The default {@link PersistentStreamScheduledExecutorBuilder}.
     */
    static PersistentStreamScheduledExecutorBuilder defaultFactory() {
        return (threadCount, streamName) -> Executors.newScheduledThreadPool(
                threadCount, new AxonThreadFactory("PersistentStream[" + streamName + "]")
        );
    }
}
