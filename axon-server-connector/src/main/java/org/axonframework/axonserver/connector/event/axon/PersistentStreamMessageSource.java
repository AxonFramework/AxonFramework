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

import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.SubscribableEventSource;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * A {@link SubscribableEventSource} that receives events from a persistent stream from Axon Server.
 * <p>
 * The persistent stream is identified by a unique name, which serves as an identifier for the {@link PersistentStream}
 * connection with Axon Server. Using the same name for different instances will overwrite the existing connection.
 * <p>
 * This implementation bridges the Axon Server persistent stream with the Axon Framework 5 async-native API by:
 * <ul>
 *   <li>Converting events using {@link PersistentStreamEventConverter}</li>
 *   <li>Providing {@link TrackingToken} via the event entry's Context</li>
 *   <li>Supporting optional client-side event filtering</li>
 * </ul>
 *
 * @author Marc Gathier
 * @author Mateusz Nowak
 * @since 4.10.0
 */
public class PersistentStreamMessageSource implements SubscribableEventSource, DescribableComponent {

    private final PersistentStreamConnection persistentStreamConnection;
    private final String name;

    private BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> consumer = NO_OP_CONSUMER;
    private static final BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> NO_OP_CONSUMER =
            (events, context) -> CompletableFuture.completedFuture(null);

    /**
     * Instantiates a {@code PersistentStreamMessageSource}.
     *
     * @param name                       The name of the persistent stream. It's a unique identifier of the
     *                                   {@link PersistentStream} connection with Axon Server. Usage of the same name
     *                                   will overwrite the existing connection.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     * @param eventConverter             The converter for transforming persistent stream events.
     */
    public PersistentStreamMessageSource(String name,
                                         Configuration configuration,
                                         PersistentStreamProperties persistentStreamProperties,
                                         ScheduledExecutorService scheduler,
                                         int batchSize,
                                         PersistentStreamEventConverter eventConverter) {
        this(name, configuration, persistentStreamProperties, scheduler, batchSize, null, eventConverter, entry -> true);
    }


    /**
     * Instantiates a {@code PersistentStreamMessageSource}.
     *
     * @param name                       The name of the persistent stream. It's a unique identifier of the
     *                                   {@link PersistentStream} connection with Axon Server. Usage of the same name
     *                                   will overwrite the existing connection.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     * @param context                    The context in which this persistent stream exists (or needs to be created).
     * @param eventConverter             The converter for transforming persistent stream events.
     */
    public PersistentStreamMessageSource(String name,
                                         Configuration configuration,
                                         PersistentStreamProperties persistentStreamProperties,
                                         ScheduledExecutorService scheduler,
                                         int batchSize,
                                         String context,
                                         PersistentStreamEventConverter eventConverter) {
        this(name, configuration, persistentStreamProperties, scheduler, batchSize, context, eventConverter, entry -> true);
    }

    /**
     * Instantiates a {@code PersistentStreamMessageSource} with event filtering support.
     *
     * @param name                       The name of the persistent stream. It's a unique identifier of the
     *                                   {@link PersistentStream} connection with Axon Server. Usage of the same name
     *                                   will overwrite the existing connection.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     * @param context                    The context in which this persistent stream exists (or needs to be created).
     *                                   May be {@code null}.
     * @param eventConverter             The converter for transforming persistent stream events.
     * @param eventFilter                A predicate to filter events after conversion. Events not matching
     *                                   the filter will be excluded from the batch sent to the consumer.
     */
    public PersistentStreamMessageSource(String name,
                                         Configuration configuration,
                                         PersistentStreamProperties persistentStreamProperties,
                                         ScheduledExecutorService scheduler,
                                         int batchSize,
                                         String context,
                                         PersistentStreamEventConverter eventConverter,
                                         Predicate<MessageStream.Entry<EventMessage>> eventFilter) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.persistentStreamConnection = new PersistentStreamConnection(
                name,
                configuration,
                persistentStreamProperties,
                scheduler,
                batchSize,
                context,
                eventConverter,
                eventFilter
        );
    }

    @Override
    public Registration subscribe(
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer
    ) {
        Objects.requireNonNull(eventsBatchConsumer, "eventsBatchConsumer must not be null");

        synchronized (this) {
            boolean noConsumer = this.consumer.equals(NO_OP_CONSUMER);
            if (noConsumer) {
                persistentStreamConnection.open(entries -> {
                    // Extract EventMessages from entries
                    List<EventMessage> events = entries.stream()
                                                       .map(MessageStream.Entry::message)
                                                       .toList();

                    // Only call consumer if there are events
                    if (!events.isEmpty()) {
                        // Pass null ProcessingContext - the SubscribingEventProcessor will create its own UnitOfWork.
                        // The TrackingToken is available in each Entry's context if needed by downstream handlers,
                        // but for subscribing processors it's not typically required.
                        eventsBatchConsumer.apply(events, null).join();
                    }
                });
                this.consumer = eventsBatchConsumer;
            } else {
                boolean sameConsumer = this.consumer.equals(eventsBatchConsumer);
                if (!sameConsumer) {
                    throw new IllegalStateException(
                            String.format(
                                    "%s: Cannot subscribe to PersistentStreamMessageSource with another consumer: there is already an active subscription.",
                                    name));
                }
            }
        }
        return () -> {
            synchronized (this) {
                persistentStreamConnection.close();
                this.consumer = NO_OP_CONSUMER;
                return true;
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation filters events by their {@link EventMessage#type() type}. Since persistent streams
     * from Axon Server do not provide tags on the client side after conversion, tag-based filtering passes
     * an empty set for tags to the {@link EventCriteria#matches(org.axonframework.messaging.core.QualifiedName, java.util.Set) matches} method.
     */
    @Override
    public Registration subscribe(
            @Nonnull EventCriteria criteria,
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer
    ) {
        Objects.requireNonNull(criteria, "EventCriteria must not be null");
        Objects.requireNonNull(eventsBatchConsumer, "eventsBatchConsumer must not be null");

        // Wrap the consumer with filtering logic
        BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> filteringConsumer =
                (events, context) -> {
                    // Filter events by criteria
                    // Note: Persistent streams from Axon Server don't provide tags client-side,
                    // so we pass empty tags for filtering. Filtering is primarily by event type.
                    List<? extends EventMessage> filteredEvents = events.stream()
                            .filter(event -> criteria.matches(
                                    event.type().qualifiedName(),
                                    Collections.emptySet()
                            ))
                            .toList();

                    if (filteredEvents.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return eventsBatchConsumer.apply(filteredEvents, context);
                };

        return subscribe(filteringConsumer);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("connection", persistentStreamConnection);
    }
}
