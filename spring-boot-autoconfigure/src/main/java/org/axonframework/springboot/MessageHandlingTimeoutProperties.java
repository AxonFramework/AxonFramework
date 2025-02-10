package org.axonframework.springboot;

import org.axonframework.messaging.timeout.HandlerTimeoutConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for message handling timeouts.
 *
 * @author Mitchell Herrijgers
 * @since 4.11
 */
@ConfigurationProperties(prefix = "axon.timeout.handlers")
public class MessageHandlingTimeoutProperties {

    /**
     * Timeout configuration for event handlers. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second.
     */
    private TimeoutForType events = new TimeoutForType();

    /**
     * Timeout configuration for command handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     */
    private TimeoutForType commands = new TimeoutForType();

    /**
     * Timeout configuration for query handlers. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second.
     */
    private TimeoutForType queries = new TimeoutForType();

    /**
     * Timeout configuration for deadline handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     */
    private TimeoutForType deadlines = new TimeoutForType();

    /**
     * The timeout configuration for event handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for event handlers
     */
    public TimeoutForType getEvents() {
        return events;
    }

    /**
     * Sets the timeout configuration for event handlers.
     *
     * @param events the timeout configuration for event handlers
     */
    public void setEvents(TimeoutForType events) {
        this.events = events;
    }

    /**
     * The timeout configuration for command handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for command handlers
     */
    public TimeoutForType getCommands() {
        return commands;
    }

    /**
     * Sets the timeout configuration for command handlers.
     *
     * @param commands the timeout configuration for command handlers
     */
    public void setCommands(TimeoutForType commands) {
        this.commands = commands;
    }

    /**
     * The timeout configuration for query handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for query handlers
     */
    public TimeoutForType getQueries() {
        return queries;
    }

    /**
     * Sets the timeout configuration for query handlers.
     *
     * @param queries the timeout configuration for query handlers
     */
    public void setQueries(TimeoutForType queries) {
        this.queries = queries;
    }

    /**
     * The timeout configuration for deadline handlers. Defaults to 10-second timeout, 8-second warning threshold and a
     * warning interval of 1 second.
     *
     * @return the timeout configuration for deadline handlers
     */
    public TimeoutForType getDeadlines() {
        return deadlines;
    }

    /**
     * Sets the timeout configuration for deadline handlers.
     *
     * @param deadlines the timeout configuration for deadline handlers
     */
    public void setDeadlines(TimeoutForType deadlines) {
        this.deadlines = deadlines;
    }

    /**
     * Configuration properties for a specific type of message handler. Defaults to 10-second timeout, 8-second warning
     * threshold and a warning interval of 1 second.
     */
    public static class TimeoutForType {

        /**
         * The timeout of the message handler in milliseconds. Defaults to 10000.
         */
        private int timeoutMs = 10000;
        /**
         * The threshold in milliseconds after which a warning is logged. Setting this to a value higher than
         * {@code timeout} will disable warnings. Defaults to 8000.
         */
        private int warningThresholdMs = 8000;

        /**
         * The interval in milliseconds between warnings. Defaults to 1000.
         */
        private int warningIntervalMs = 1000;

        /**
         * Returns the timeout of the message handler in milliseconds.
         *
         * @return the timeout of the message handler in milliseconds
         */
        public int getTimeoutMs() {
            return timeoutMs;
        }

        /**
         * Sets the timeout of the message handler in milliseconds.
         *
         * @param timeoutMs the timeout of the message handler in milliseconds
         */
        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        /**
         * Returns the threshold in milliseconds after which a warning is logged. Setting this to a value higher than
         * {@code timeout} will disable warnings.
         *
         * @return the threshold in milliseconds after which a warning is logged
         */
        public int getWarningThresholdMs() {
            return warningThresholdMs;
        }

        /**
         * Sets the threshold in milliseconds after which a warning is logged. Setting this to a value higher than
         * {@code timeout} will disable warnings.
         *
         * @param warningThresholdMs the threshold in milliseconds after which a warning is logged
         */
        public void setWarningThresholdMs(int warningThresholdMs) {
            this.warningThresholdMs = warningThresholdMs;
        }

        /**
         * Returns the interval in milliseconds between warnings.
         *
         * @return the interval in milliseconds between warnings
         */
        public int getWarningIntervalMs() {
            return warningIntervalMs;
        }

        /**
         * Sets the interval in milliseconds between warnings.
         *
         * @param warningIntervalMs the interval in milliseconds between warnings
         */
        public void setWarningIntervalMs(int warningIntervalMs) {
            this.warningIntervalMs = warningIntervalMs;
        }
    }

    /**
     * Converts this configuration to a {@link HandlerTimeoutConfiguration}.
     *
     * @return the {@link HandlerTimeoutConfiguration} based on this configuration
     */
    public HandlerTimeoutConfiguration toMessageHandlerTimeoutConfiguration() {
        return new HandlerTimeoutConfiguration(
                new HandlerTimeoutConfiguration.TimeoutForType(events.timeoutMs,
                                                               events.warningThresholdMs,
                                                               events.warningIntervalMs),
                new HandlerTimeoutConfiguration.TimeoutForType(commands.timeoutMs,
                                                               commands.warningThresholdMs,
                                                               commands.warningIntervalMs),
                new HandlerTimeoutConfiguration.TimeoutForType(queries.timeoutMs,
                                                               queries.warningThresholdMs,
                                                               queries.warningIntervalMs),
                new HandlerTimeoutConfiguration.TimeoutForType(deadlines.timeoutMs,
                                                               deadlines.warningThresholdMs,
                                                               deadlines.warningIntervalMs)
        );
    }
}
