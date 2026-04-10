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

package org.axonframework.extension.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot configuration properties for Dead Letter Queue (DLQ) settings per event processor.
 * <p>
 * Binds to the {@code axon.eventhandling.processors.<name>.dlq.*} property namespace. Each processor can independently
 * enable or disable dead-lettering and tune cache settings:
 * <pre>{@code
 * axon:
 *   eventhandling:
 *     processors:
 *       my-processor:
 *         dlq:
 *           enabled: true
 *           cache:
 *             size: 2048
 * }</pre>
 * <p>
 * When no properties are configured for a processor, {@link #forProcessor(String)} returns defaults (DLQ disabled,
 * cache size 1024).
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@ConfigurationProperties("axon.eventhandling")
public class DeadLetterQueueProcessorProperties {

    /**
     * Per-processor DLQ settings, keyed by processor name.
     */
    private final Map<String, DlqProcessorSettings> processors = new HashMap<>();

    /**
     * Returns the per-processor DLQ settings map.
     *
     * @return the map of processor names to their DLQ settings
     */
    public Map<String, DlqProcessorSettings> getProcessors() {
        return processors;
    }

    /**
     * Returns the DLQ settings for the given processor name. If no settings are configured for this processor, returns
     * defaults (DLQ disabled, cache size 1024).
     *
     * @param processorName The name of the event processor.
     * @return The DLQ settings for the processor, never {@code null}.
     */
    public DlqProcessorSettings forProcessor(String processorName) {
        return processors.getOrDefault(processorName, new DlqProcessorSettings());
    }

    /**
     * Per-processor settings containing DLQ configuration.
     */
    public static class DlqProcessorSettings {

        private Dlq dlq = new Dlq();

        /**
         * Returns the DLQ settings for this processor.
         *
         * @return The DLQ settings.
         */
        public Dlq getDlq() {
            return dlq;
        }

        /**
         * Sets the DLQ settings for this processor.
         *
         * @param dlq The DLQ settings.
         */
        public void setDlq(Dlq dlq) {
            this.dlq = dlq;
        }
    }

    /**
     * Configuration for the Dead Letter Queue (DLQ) on a single event processor.
     */
    public static class Dlq {

        /**
         * Whether the DLQ is enabled for this processor. Defaults to {@code false}.
         */
        private boolean enabled = false;

        /**
         * Cache settings for the DLQ sequence identifier cache.
         */
        private DlqCache cache = new DlqCache();

        /**
         * Returns whether the DLQ is enabled for this processor.
         *
         * @return {@code true} if DLQ is enabled, {@code false} otherwise.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether the DLQ is enabled for this processor.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the cache settings for the DLQ sequence identifier cache.
         *
         * @return The cache settings.
         */
        public DlqCache getCache() {
            return cache;
        }

        /**
         * Sets the cache settings for the DLQ sequence identifier cache.
         *
         * @param cache The cache settings.
         */
        public void setCache(DlqCache cache) {
            this.cache = cache;
        }
    }

    /**
     * Cache settings for the DLQ sequence identifier in-memory cache.
     */
    public static class DlqCache {

        /**
         * Maximum number of sequence identifiers kept in memory per segment. Setting this to {@code 0} disables the
         * caching wrapper entirely. Defaults to {@code 1024}.
         */
        private int size = 1024;

        /**
         * Returns the maximum cache size per segment.
         *
         * @return The cache size, or {@code 0} if caching is disabled.
         */
        public int getSize() {
            return size;
        }

        /**
         * Sets the maximum cache size per segment. Setting this to {@code 0} disables the caching wrapper entirely.
         *
         * @param size The maximum cache size.
         */
        public void setSize(int size) {
            this.size = size;
        }
    }
}
