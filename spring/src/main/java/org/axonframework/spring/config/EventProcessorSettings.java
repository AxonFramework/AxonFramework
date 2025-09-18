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

package org.axonframework.spring.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessor;

/**
 * Event processor settings.
 */
public sealed interface EventProcessorSettings {

    /**
     * The processing modes of an {@link EventProcessor}.
     */
    enum ProcessorMode {
        /**
         * Indicates a {@link org.axonframework.eventhandling.processors.subscribing.SubscribingEventProcessor} should
         * be used.
         */
        SUBSCRIBING,
        /**
         * Indicates a {@link PooledStreamingEventProcessor} should be used.
         */
        POOLED
    }

    /**
     * Retrieves the processor mode.
     *
     * @return processor mode.
     */
    @Nonnull
    ProcessorMode getProcessorMode();

    /**
     * Name of the bean acting as source for this processor.
     *
     * @return only used if non-null.
     */
    @Nullable
    String getSource();

    /**
     * Settings for subscribing event processor.
     */
    non-sealed interface SubscribingEventProcessorSettings extends EventProcessorSettings {

        @Nonnull
        @Override
        default ProcessorMode getProcessorMode() {
            return ProcessorMode.SUBSCRIBING;
        }
    }

    /**
     * Settings for pooled event processor.
     */
    non-sealed interface PooledEventProcessorSettings extends EventProcessorSettings {

        /**
         * Retrieves the processor mode.
         *
         * @return processor mode.
         */
        @Nonnull
        default ProcessorMode getProcessorMode() {
            return ProcessorMode.POOLED;
        }

        /**
         * Initial segment count.
         *
         * @return returns initial segment count.
         */
        int getInitialSegmentCount();

        /**
         * Retrieves token claim interval.
         *
         * @return interval in milliseconds.
         */
        long getTokenClaimIntervalInMillis();

        /**
         * Thread count for pooled processor.
         *
         * @return a positive integer describing the size of the thread pool.
         */
        int getThreadCount();

        /**
         * Batch size for the pooled processor.
         *
         * @return a positive integer describing the size of the batch-
         */
        int getBatchSize();
    }
}
