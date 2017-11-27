/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("axon.eventhandling")
public class EventProcessorProperties {

    /**
     * The configuration of each of the processors. The key is the name of the processor, the value represents the
     * settings to use for the processor with that name.
     */
    private Map<String, ProcessorSettings> processors = new HashMap<>();

    public Map<String, ProcessorSettings> getProcessors() {
        return processors;
    }

    public enum Mode {

        TRACKING,
        SUBSCRIBING
    }

    public static class ProcessorSettings {

        /**
         * Sets the source for this processor. Defaults to streaming from/subscribing to the Event Bus
         */
        private String source;

        /**
         * Indicates whether this processor should be Tracking, or Subscribing its source
         */
        private Mode mode = Mode.SUBSCRIBING;

        /**
         * Indicates the number of segments that should be created when the processor starts for the first time
         */
        private int initialSegmentCount;

        /**
         * The maximum number of threads the processor should process events with
         */
        private int threadCount = 1;

        /**
         * The maximum number of events a processor should process as part of a single batch
         */
        private int batchSize = 1;

        private String sequencingPolicy;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public int getInitialSegmentCount() {
            return initialSegmentCount;
        }

        public void setInitialSegmentCount(int initialSegmentCount) {
            this.initialSegmentCount = initialSegmentCount;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public String getSequencingPolicy() {
            return sequencingPolicy;
        }

        public void setSequencingPolicy(String sequencingPolicy) {
            this.sequencingPolicy = sequencingPolicy;
        }
    }
}
