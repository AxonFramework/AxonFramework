package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.config.Configuration;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Functional interface for creating instances of {@link PersistentStreamMessageSource}.
 * This factory is used to construct message sources for persistent streams with specific configurations.
 */
@FunctionalInterface
public interface PersistentStreamMessageSourceFactory {

    /**
     * Builds a new instance of {@link PersistentStreamMessageSource} with the specified parameters.
     *
     * @param name The name of the persistent stream. This is used to identify the stream.
     * @param persistentStreamProperties The properties of the persistent stream, containing configuration details.
     * @param scheduler The {@link ScheduledExecutorService} to be used for scheduling tasks related to the message source.
     * @param batchSize The number of events to be fetched in a single batch from the stream.
     * @param context The context in which the persistent stream operates. This can be used to differentiate streams in different environments or applications.
     * @return A new instance of {@link PersistentStreamMessageSource} configured with the provided parameters.
     * @throws IllegalArgumentException if any of the required parameters are null or invalid.
     * @throws org.axonframework.axonserver.connector.AxonServerException if there's an issue connecting to or configuring the Axon Server.
     */
    PersistentStreamMessageSource build(
            String name,
            PersistentStreamProperties persistentStreamProperties,
            ScheduledExecutorService scheduler,
            int batchSize,
            String context,
            Configuration configuration);
}