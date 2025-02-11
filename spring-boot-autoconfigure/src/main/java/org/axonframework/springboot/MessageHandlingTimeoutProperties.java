package org.axonframework.springboot;

import org.axonframework.messaging.timeout.HandlerTimeoutConfiguration;
import org.axonframework.messaging.timeout.TaskTimeoutSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for message handling timeouts.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
@ConfigurationProperties(prefix = "axon.timeout.handlers")
public class MessageHandlingTimeoutProperties {

    /**
     * Timeout configuration for event handlers. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings events = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout configuration for command handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings commands = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout configuration for query handlers. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings queries = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout configuration for deadline handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings deadlines = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * The timeout configuration for event handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for event handlers
     */
    public TaskTimeoutSettings getEvents() {
        return events;
    }

    /**
     * Sets the timeout configuration for event handlers.
     *
     * @param events the timeout configuration for event handlers
     */
    public void setEvents(TaskTimeoutSettings events) {
        this.events = events;
    }

    /**
     * The timeout configuration for command handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for command handlers
     */
    public TaskTimeoutSettings getCommands() {
        return commands;
    }

    /**
     * Sets the timeout configuration for command handlers.
     *
     * @param commands the timeout configuration for command handlers
     */
    public void setCommands(TaskTimeoutSettings commands) {
        this.commands = commands;
    }

    /**
     * The timeout configuration for query handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for query handlers
     */
    public TaskTimeoutSettings getQueries() {
        return queries;
    }

    /**
     * Sets the timeout configuration for query handlers.
     *
     * @param queries the timeout configuration for query handlers
     */
    public void setQueries(TaskTimeoutSettings queries) {
        this.queries = queries;
    }

    /**
     * The timeout configuration for deadline handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for deadline handlers
     */
    public TaskTimeoutSettings getDeadlines() {
        return deadlines;
    }

    /**
     * Sets the timeout configuration for deadline handlers.
     *
     * @param deadlines the timeout configuration for deadline handlers
     */
    public void setDeadlines(TaskTimeoutSettings deadlines) {
        this.deadlines = deadlines;
    }

    /**
     * Converts this configuration to a {@link HandlerTimeoutConfiguration}.
     *
     * @return the {@link HandlerTimeoutConfiguration} based on this configuration
     */
    public HandlerTimeoutConfiguration toMessageHandlerTimeoutConfiguration() {
        return new HandlerTimeoutConfiguration(events, commands, queries, deadlines);
    }
}
