/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.springboot;

import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Properties describing the settings for Event Processors.
 */
@ConfigurationProperties("axon.eventhandling")
public class EventProcessorProperties {

    /**
     * The configuration of each of the processors. The key is the name of the processor, the value represents the
     * settings to use for the processor with that name.
     */
    private final Map<String, ProcessorSettings> processors = new HashMap<>();

    /**
     * Returns the settings for each of the configured processors, by name.
     *
     * @return the settings for each of the configured processors, by name.
     */
    public Map<String, ProcessorSettings> getProcessors() {
        return processors;
    }

    /**
     * The processing mode of the processor.
     */
    public enum Mode {
        /**
         * Indicates a Tracking Processor should be used.
         */
        TRACKING,
        /**
         * Indicates a Subscribing Processor should be used.
         */
        SUBSCRIBING
    }

    public static class ProcessorSettings {

        /**
         * Sets the source for this processor. Defaults to streaming from/subscribing to the Event Bus.
         */
        private String source;

        /**
         * Indicates whether this processor should be Tracking, or Subscribing its source. Defaults to Tracking.
         */
        private Mode mode = Mode.TRACKING;

        /**
         * Indicates the number of segments that should be created when the processor starts for the first time.
         * Defaults to 1.
         */
        private int initialSegmentCount = 1;

        /**
         * The interval between attempts to claim tokens by a {@link org.axonframework.eventhandling.TrackingEventProcessor}.
         *
         * Defaults to 5000 milliseconds.
         */
        private long tokenClaimInterval = 5000;

        /**
         * The time unit of tokens claim interval.
         *
         * Defaults to {@link TimeUnit#MILLISECONDS}.
         */
        private TimeUnit tokenClaimIntervalTimeUnit = TimeUnit.MILLISECONDS;

        /**
         * The maximum number of threads the processor should process events with. Defaults to the number of initial
         * segments if this is not further specified. Defaults to 1.
         */
        private int threadCount = -1;

        /**
         * The maximum number of events a processor should process as part of a single batch.
         */
        private int batchSize = 1;

        /**
         * The name of the bean that represents the sequencing policy for processing events. If no name is specified,
         * the processor defaults to a {@link org.axonframework.eventhandling.async.SequentialPerAggregatePolicy}, which
         * guarantees to process events originating from the same Aggregate instance sequentially, while events from
         * different Aggregate instances may be processed concurrently.
         */
        private String sequencingPolicy;

        /**
         * Returns the name of the bean that should be used as source for Event Messages. If not provided, the
         * {@link org.axonframework.eventhandling.EventBus} is used as source.
         *
         * @return the name of the bean that should be used as source for Event Messages.
         */
        public String getSource() {
            return source;
        }

        /**
         * Sets the name of the bean that should be used as source for Event Messages.
         *
         * @param source the name of the bean that should be used as source for Event Messages.
         */
        public void setSource(String source) {
            this.source = source;
        }

        /**
         * Returns the type of processor to configure.
         *
         * @return the type of processor to configure.
         */
        public Mode getMode() {
            return mode;
        }

        /**
         * Sets the type of processor that should be configured. Defaults to "subscribing".
         *
         * @param mode the type of processor that should be configured.
         */
        public void setMode(Mode mode) {
            this.mode = mode;
        }

        /**
         * Returns the number of initial segments that should be created, if no segments are already present. Is also
         * used as the thread count if it is not further specified.
         *
         * @return the number of initial segments that should be created.
         */
        public int getInitialSegmentCount() {
            return initialSegmentCount;
        }

        /**
         * Sets the number of initial segments that should be created, if no segments are already present. Defaults to
         * 1. Will also be used as the thread count if it is not further specified.
         *
         * @param initialSegmentCount the number of initial segments that should be created.
         */
        public void setInitialSegmentCount(int initialSegmentCount) {
            this.initialSegmentCount = initialSegmentCount;
        }

        /**
         * Returns the interval between attempts to claim tokens by a {@link org.axonframework.eventhandling.TrackingEventProcessor}.
         * Defaults to 5000 milliseconds.
         *
         * @return the interval between attempts to claim tokens by a {@link org.axonframework.eventhandling.TrackingEventProcessor}.
         */
        public long getTokenClaimInterval() {
            return tokenClaimInterval;
        }

        /**
         * Sets the time to wait after a failed attempt to claim any token, before making another attempt.
         * Defaults to 5000 milliseconds.
         *
         * @param tokenClaimInterval the interval between attempts to claim tokens by a {@link org.axonframework.eventhandling.TrackingEventProcessor}.
         */
        public void setTokenClaimInterval(long tokenClaimInterval) {
            this.tokenClaimInterval = tokenClaimInterval;
        }

        /**
         * Returns the time unit used to define tokens claim interval. Defaults to MILLISECONDS.
         *
         * @return the time unit used to defined tokens claim interval.
         */
        public TimeUnit getTokenClaimIntervalTimeUnit() {
            return tokenClaimIntervalTimeUnit;
        }

        /**
         * Sets the time unit used to defined tokens claim interval. It must be a valid value of {@link TimeUnit}.
         * Defaults to MILLISECONDS.
         *
         * @param tokenClaimIntervalTimeUnit the time unit used to defined tokens claim interval.
         */
        public void setTokenClaimIntervalTimeUnit(TimeUnit tokenClaimIntervalTimeUnit) {
            this.tokenClaimIntervalTimeUnit = tokenClaimIntervalTimeUnit;
        }

        /**
         * Returns the number of threads to use to process Events, when in "tracking" mode. Defaults to the number of
         * initial segments.
         *
         * @return the number of threads to use to process Events.
         */
        public int getThreadCount() {
            return threadCount < 0 ? initialSegmentCount : threadCount;
        }

        /**
         * Sets the number of threads to use to process Events, when in "tracking" mode. Defaults to the number of
         * initial segments.
         * <p>
         * A provided {@code threadCount} < 0 will result in a number of threads equal to the configured number of
         * {@link #setInitialSegmentCount(int) initial segments}.
         *
         * @param threadCount the number of threads to use to process Events.
         */
        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }

        /**
         * Returns the maximum size of a processing batch. This is the number of events that a processor in "tracking"
         * mode will attempt to read and process within a single Unit of Work / Transaction.
         *
         * @return the maximum size of a processing batch.
         */
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * Sets the maximum size of a processing batch. This is the number of events that a processor in "tracking"
         * mode will attempt to read and process within a single Unit of Work / Transaction. Defaults to 1.
         *
         * @param batchSize the maximum size of a processing batch.
         */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /**
         * Returns the name of the bean that defines the Sequencing Policy for this processor.
         *
         * @return the name of the bean that defines the Sequencing Policy for this processor.
         */
        public String getSequencingPolicy() {
            return sequencingPolicy;
        }

        /**
         * Sets the name of the bean that defines the Sequencing Policy for this processor. The Sequencing Policy
         * describes which Events must be handled sequentially, and which can be handled concurrently. Defaults to
         * a "SequentialPerAggregatePolicy".
         *
         * @param sequencingPolicy the name of the bean that defines the Sequencing Policy for this processor.
         */
        public void setSequencingPolicy(String sequencingPolicy) {
            this.sequencingPolicy = sequencingPolicy;
        }
    }
}
