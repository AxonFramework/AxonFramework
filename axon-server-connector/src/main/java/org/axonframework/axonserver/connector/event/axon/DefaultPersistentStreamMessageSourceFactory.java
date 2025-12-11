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
package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.common.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default implementation of the {@link PersistentStreamMessageSourceFactory} that creates
 * {@link PersistentStreamMessageSource} instances.
 * <p>
 * This factory maintains a record of used stream names and provides warning logs when name conflicts occur.
 * Each created {@link PersistentStreamMessageSource} is configured with a {@link PersistentStreamEventConverter}
 * for proper event conversion following the Axon Framework 5 async-native API patterns.
 *
 * @author Mateusz Nowak
 * @since 4.11
 */
public class DefaultPersistentStreamMessageSourceFactory implements PersistentStreamMessageSourceFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPersistentStreamMessageSourceFactory.class);
    private final Set<String> usedNames = new CopyOnWriteArraySet<>();

    /**
     * Creates a new {@code PersistentStreamMessageSource}. This method tracks stream names and logs warnings when name
     * conflicts are detected.
     * <p>
     * The created message source uses a {@link PersistentStreamEventConverter} for converting Axon Server's
     * persistent stream events into Axon Framework's event messages with proper tracking token and aggregate
     * resource handling.
     *
     * @param name                       The name of the persistent stream. It's a unique identifier of the
     *                                   PersistentStream connection with Axon Server. Usage of the same name will
     *                                   overwrite the existing connection.
     * @param persistentStreamProperties The properties to create the persistent stream.
     * @param scheduler                  Scheduler used for persistent stream operations.
     * @param batchSize                  The batch size for collecting events.
     * @param context                    The context in which this persistent stream exists (or needs to be created).
     *                                   May be {@code null}.
     * @param configuration              Global configuration of Axon components.
     * @return A new {@link PersistentStreamMessageSource} instance.
     */
    @Override
    public PersistentStreamMessageSource build(String name,
                                               PersistentStreamProperties persistentStreamProperties,
                                               ScheduledExecutorService scheduler,
                                               int batchSize,
                                               String context,
                                               Configuration configuration) {
        if (!usedNames.add(name)) {
            logger.warn(
                    "A Persistent Stream connection with Axon Server is uniquely identified based on the name. "
                            + "Another Persistent Stream is started for a given name [{}]. "
                            + "The new connection will overwrite the existing connection.",
                    name);
        }
        PersistentStreamEventConverter eventConverter = new PersistentStreamEventConverter();
        return new PersistentStreamMessageSource(
                name,
                configuration,
                persistentStreamProperties,
                scheduler,
                batchSize,
                context,
                eventConverter
        );
    }
}
