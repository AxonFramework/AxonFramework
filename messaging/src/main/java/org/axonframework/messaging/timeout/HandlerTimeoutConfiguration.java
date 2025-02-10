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

package org.axonframework.messaging.timeout;

/**
 * Configuration object for the timeout settings of handlers.
 *
 * @author Mitchell Herrijgers
 * @since 4.11
 */
public class HandlerTimeoutConfiguration {

    /**
     * Timeout settings for events.
     */
    private final TimeoutForType events;

    /**
     * Timeout settings for commands.
     */
    private final TimeoutForType commands;

    /**
     * Timeout settings for queries.
     */
    private final TimeoutForType queries;

    /**
     * Timeout settings for deadlines.
     */
    private final TimeoutForType deadlines;

    /**
     * Creates a new {@link HandlerTimeoutConfiguration} with default timeout settings. This means all message handlers
     * have a timeout of 10 seconds, with a warning threshold of 8 seconds and a warning interval of 1 second.
     */
    public HandlerTimeoutConfiguration() {
        this(new TimeoutForType(), new TimeoutForType(), new TimeoutForType(), new TimeoutForType());
    }

    /**
     * Creates a new {@link HandlerTimeoutConfiguration} with the given timeout settings.
     *
     * @param events    the timeout settings for events
     * @param commands  the timeout settings for commands
     * @param queries   the timeout settings for queries
     * @param deadlines the timeout settings for deadlines
     */
    public HandlerTimeoutConfiguration(TimeoutForType events, TimeoutForType commands, TimeoutForType queries,
                                       TimeoutForType deadlines) {
        this.events = events;
        this.commands = commands;
        this.queries = queries;
        this.deadlines = deadlines;
    }

    /**
     * Retrieves the timeout settings for events.
     *
     * @return the timeout settings for events
     */
    public TimeoutForType getEvents() {
        return events;
    }

    /**
     * Retrieves the timeout settings for commands.
     *
     * @return the timeout settings for commands
     */
    public TimeoutForType getCommands() {
        return commands;
    }

    /**
     * Retrieves the timeout settings for queries.
     *
     * @return the timeout settings for queries
     */
    public TimeoutForType getQueries() {
        return queries;
    }

    /**
     * Retrieves the timeout settings for deadlines.
     *
     * @return the timeout settings for deadlines
     */
    public TimeoutForType getDeadlines() {
        return deadlines;
    }

    /**
     * Timeout settings for a specific message type.
     */
    public static class TimeoutForType {

        private final int timeoutMs;
        private final int warningThresholdMs;
        private final int warningIntervalMs;

        /**
         * Creates a new {@link TimeoutForType} with default timeout settings. This means all message handlers have a
         * timeout of 10 seconds, with a warning threshold of 8 seconds and a warning interval of 1 second.
         */
        public TimeoutForType() {
            this(10000, 8000, 1000);
        }

        /**
         * Creates a new {@link TimeoutForType} with the given timeout settings.
         *
         * @param timeoutMs          the timeout in milliseconds
         * @param warningThresholdMs the threshold in milliseconds after which a warning is logged. Setting this to a
         *                           value higher than {@code timeout} will disable warnings.
         * @param warningIntervalMs  the interval in milliseconds between warnings
         */
        public TimeoutForType(int timeoutMs, int warningThresholdMs, int warningIntervalMs) {
            this.timeoutMs = timeoutMs;
            this.warningThresholdMs = warningThresholdMs;
            this.warningIntervalMs = warningIntervalMs;
        }

        /**
         * Retrieves the timeout in milliseconds.
         *
         * @return the timeout in milliseconds
         */
        public int getTimeoutMs() {
            return timeoutMs;
        }

        /**
         * Retrieves the threshold in milliseconds after which a warning is logged. Setting this to a value higher than
         * the timeout will disable warnings.
         *
         * @return the threshold in milliseconds after which a warning is logged
         */
        public int getWarningThresholdMs() {
            return warningThresholdMs;
        }

        /**
         * Retrieves the interval in milliseconds between warnings.
         *
         * @return the interval in milliseconds between warnings
         */
        public int getWarningIntervalMs() {
            return warningIntervalMs;
        }
    }
}
