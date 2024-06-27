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
import org.axonframework.common.Registration;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Subscribable message source that receives event from a persistent stream from Axon Server.
 */
public class PersistentStreamMessageSource implements SubscribableMessageSource<EventMessage<?>> {
    private final PersistentStreamConnection persistentStreamConnection;

    /**
     * Instantiates a {@code PersistentStreamMessageSource}.
     * @param name the name of the event processor
     * @param configuration global configuration of Axon components
     * @param persistentStreamProperties properties for the persistent stream
     * @param scheduler scheduler thread pool to schedule tasks
     * @param batchSize the batch size for collecting events
     */
    public PersistentStreamMessageSource(String name, Configuration configuration, PersistentStreamProperties
            persistentStreamProperties, ScheduledExecutorService scheduler, int batchSize) {
        persistentStreamConnection = new PersistentStreamConnection(name, configuration,
                                        persistentStreamProperties, scheduler, batchSize);
    }


    /**
     * Instantiates a {@code PersistentStreamMessageSource}.
     * @param name the name of the event processor
     * @param configuration global configuration of Axon components
     * @param persistentStreamProperties properties for the persistent stream
     * @param scheduler scheduler thread pool to schedule tasks
     * @param batchSize the batch size for collecting events
     */
    public PersistentStreamMessageSource(String name, Configuration configuration, PersistentStreamProperties
            persistentStreamProperties, ScheduledExecutorService scheduler, int batchSize, String defaultContext) {
        persistentStreamConnection = new PersistentStreamConnection(name, configuration,
                persistentStreamProperties, scheduler, batchSize, defaultContext);
    }

    @Override
    public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> consumer) {
        persistentStreamConnection.open(consumer);
        return () -> {
            persistentStreamConnection.close();
            return true;
        };
    }
}
