/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.config.Configuration;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Functional interface for creating instances of a {@link PersistentStreamMessageSource}.
 * <p>
 * This factory is used to construct message sources for persistent streams with specific configurations.
 *
 * @author Marc Gathier
 * @since 4.10.0
 */
@FunctionalInterface
public interface PersistentStreamMessageSourceFactory {

    /**
     * Builds a new instance of {@link PersistentStreamMessageSource} with the specified parameters.
     *
     * @param name                       The name of the persistent stream. This is used to identify the stream.
     * @param persistentStreamProperties The properties of the persistent stream, containing configuration details.
     * @param scheduler                  The {@link ScheduledExecutorService} to be used for scheduling tasks related to
     *                                   the message source.
     * @param batchSize                  The number of events to be fetched in a single batch from the stream.
     * @param context                    The context in which the persistent stream operates. This can be used to
     *                                   differentiate streams in different environments or applications.
     * @return A new instance of {@link PersistentStreamMessageSource} configured with the provided parameters.
     * @throws IllegalArgumentException                                   If any of the required parameters are null or
     *                                                                    invalid.
     * @throws org.axonframework.axonserver.connector.AxonServerException If there's an issue connecting to or
     *                                                                    configuring the Axon Server.
     */
    PersistentStreamMessageSource build(String name,
                                        PersistentStreamProperties persistentStreamProperties,
                                        ScheduledExecutorService scheduler,
                                        int batchSize,
                                        String context,
                                        Configuration configuration);
}