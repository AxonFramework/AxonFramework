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

import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.common.Registration;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * A {@link SubscribableMessageSource} that receives event from a persistent stream from Axon Server.
 * The persistent stream is identified by a unique name, which serves as an identifier for the
 * {@link PersistentStream} connection with Axon Server. Using the same name for different instances
 * will overwrite the existing connection.
 *
 * @author Marc Gathier
 * @since 4.10.0
 */
public class PersistentStreamMessageSource implements SubscribableMessageSource<EventMessage<?>> {

    private final PersistentStreamConnection persistentStreamConnection;

    private final AtomicReference<Consumer<List<? extends EventMessage<?>>>> consumer =
            new AtomicReference<>(NO_OP_CONSUMER);
    private static final Consumer<List<? extends EventMessage<?>>> NO_OP_CONSUMER = events -> {
    };

    /**
     * Instantiates a {@code PersistentStreamMessageSource}.
     *
     * @param name                       The name of the persistent stream. It's a unique identifier of the
     *                                   {@link PersistentStream} connection with Axon Sever. Usage of the same name
     *                                   will overwrite the existing connection.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     */
    public PersistentStreamMessageSource(String name,
                                         Configuration configuration,
                                         PersistentStreamProperties persistentStreamProperties,
                                         ScheduledExecutorService scheduler,
                                         int batchSize) {
        this(name, configuration, persistentStreamProperties, scheduler, batchSize, null);
    }


    /**
     * Instantiates a {@code PersistentStreamMessageSource}.
     *
     * @param name                       The name of the persistent stream. It's a unique identifier of the
     *                                   {@link PersistentStream} connection with Axon Sever. Usage of the same name
     *                                   will overwrite the existing connection.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     * @param context                    The context in which this persistent stream exists (or needs to be created).
     */
    public PersistentStreamMessageSource(String name,
                                         Configuration configuration,
                                         PersistentStreamProperties persistentStreamProperties,
                                         ScheduledExecutorService scheduler,
                                         int batchSize,
                                         String context) {
        persistentStreamConnection = new PersistentStreamConnection(name,
                                                                    configuration,
                                                                    persistentStreamProperties,
                                                                    scheduler,
                                                                    batchSize,
                                                                    context);
    }

    @Override
    public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> consumer) {
        if (!this.consumer.compareAndSet(NO_OP_CONSUMER, consumer)) {
            persistentStreamConnection.open(consumer);
        }
        return () -> {
            persistentStreamConnection.close();
            return true;
        };
    }
}
