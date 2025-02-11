package org.axonframework.springboot;

import org.axonframework.messaging.timeout.TaskTimeoutSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for timeouts of the {@link org.axonframework.messaging.unitofwork.UnitOfWork} during
 * processing one or multiple messages. Timeouts for individual message handlers can also be configured through
 * {@code axon.timeout.handler} properties.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
@ConfigurationProperties(prefix = "axon.timeout.transaction")
public class TransactionTimeoutProperties {

    /**
     * Timeout settings for the command bus. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second. This timeout is used for the entire command handling process.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings commandBus = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout settings for the query bus. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second. This timeout is used for the entire query handling process.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings queryBus = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout settings for deadlines. Defaults to 10-second timeout, 8-second warning threshold and a warning interval
     * of 1 second. This timeout is used for the entire deadline handling process.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings deadline = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout settings for all event processors, unless a more specific setting is registered via the
     * {@code event-processor} property. Defaults to 5-second timeout, 2-second warning threshold and a warning
     * interval of 1 second.
     */
    @NestedConfigurationProperty
    private TaskTimeoutSettings eventProcessors = new TaskTimeoutSettings(10000, 8000, 1000);

    /**
     * Timeout settings for specific event processors. The key is the name of the event processor, the value is the
     * timeout settings for that event processor. Defaults to an empty map.
     */
    private final Map<String, TaskTimeoutSettings> eventProcessor = new HashMap<>();

    /**
     * Timeout settings for the command bus. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second. This timeout is used for the entire command handling process.
     *
     * @return the timeout settings for the command bus
     */
    public TaskTimeoutSettings getCommandBus() {
        return commandBus;
    }

    /**
     * Sets the timeout settings for the command bus.
     * @param commandBus the timeout settings for the command bus
     */
    public void setCommandBus(TaskTimeoutSettings commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Timeout settings for the query bus. Defaults to 10-second timeout, 8-second warning threshold and a warning
     * interval of 1 second. This timeout is used for the entire query handling process.
     *
     * @return the timeout settings for the query bus
     */
    public TaskTimeoutSettings getQueryBus() {
        return queryBus;
    }

    /**
     * Sets the timeout settings for the query bus.
     * @param queryBus the timeout settings for the query bus
     */
    public void setQueryBus(TaskTimeoutSettings queryBus) {
        this.queryBus = queryBus;
    }

    /**
     * Timeout settings for deadlines. Defaults to 10-second timeout, 8-second warning threshold and a warning interval
     * of 1 second. This timeout is used for the entire deadline handling process.
     *
     * @return the timeout settings for deadlines
     */
    public TaskTimeoutSettings getDeadline() {
        return deadline;
    }

    /**
     * Sets the timeout settings for deadlines.
     * @param deadline the timeout settings for deadlines
     */
    public void setDeadline(TaskTimeoutSettings deadline) {
        this.deadline = deadline;
    }

    /**
     * Timeout settings for all event processors, unless a more specific setting is registered via the
     * {@code event-processor} property. Defaults to 5-second timeout, 2-second warning threshold and a warning
     * interval of 1 second.
     *
     * @return the timeout settings for event processors
     */
    public TaskTimeoutSettings getEventProcessors() {
        return eventProcessors;
    }

    /**
     * Sets the timeout settings for event processors.
     * @param eventProcessors the timeout settings for event processors
     */
    public void setEventProcessors(TaskTimeoutSettings eventProcessors) {
        this.eventProcessors = eventProcessors;
    }

    /**
     * Timeout settings for specific event processors. The key is the name of the event processor, the value is the
     * timeout settings for that event processor. Defaults to an empty map.
     *
     * @return the timeout settings for specific event processors
     */
    public Map<String, TaskTimeoutSettings> getEventProcessor() {
        return eventProcessor;
    }
}
