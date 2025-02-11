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
 * @since 4.11.0
 */
public class HandlerTimeoutConfiguration {

    /**
     * Timeout settings for events.
     */
    private final TaskTimeoutSettings events;

    /**
     * Timeout settings for commands.
     */
    private final TaskTimeoutSettings commands;

    /**
     * Timeout settings for queries.
     */
    private final TaskTimeoutSettings queries;

    /**
     * Timeout settings for deadlines.
     */
    private final TaskTimeoutSettings deadlines;

    /**
     * Creates a new {@link HandlerTimeoutConfiguration} with default timeout settings. This means all message handlers
     * have a timeout of 10 seconds, with a warning threshold of 8 seconds and a warning interval of 1 second.
     */
    public HandlerTimeoutConfiguration() {
        this(new TaskTimeoutSettings(),
             new TaskTimeoutSettings(),
             new TaskTimeoutSettings(),
             new TaskTimeoutSettings());
    }

    /**
     * Creates a new {@link HandlerTimeoutConfiguration} with the given timeout settings.
     *
     * @param events    the timeout settings for events
     * @param commands  the timeout settings for commands
     * @param queries   the timeout settings for queries
     * @param deadlines the timeout settings for deadlines
     */
    public HandlerTimeoutConfiguration(TaskTimeoutSettings events,
                                       TaskTimeoutSettings commands,
                                       TaskTimeoutSettings queries,
                                       TaskTimeoutSettings deadlines) {
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
    public TaskTimeoutSettings getEvents() {
        return events;
    }

    /**
     * Retrieves the timeout settings for commands.
     *
     * @return the timeout settings for commands
     */
    public TaskTimeoutSettings getCommands() {
        return commands;
    }

    /**
     * Retrieves the timeout settings for queries.
     *
     * @return the timeout settings for queries
     */
    public TaskTimeoutSettings getQueries() {
        return queries;
    }

    /**
     * Retrieves the timeout settings for deadlines.
     *
     * @return the timeout settings for deadlines
     */
    public TaskTimeoutSettings getDeadlines() {
        return deadlines;
    }
}
