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
import org.axonframework.axonserver.connector.event.AggregateEventConverter;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.SubscribableEventSource;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;

/**
 * A {@link SubscribableEventSource} that receives events from a persistent stream from Axon Server.
 * <p>
 * The persistent stream is identified by a unique name, which serves as an identifier for the {@link PersistentStream}
 * connection with Axon Server. Using the same name for different instances will overwrite the existing connection.
 * <p>
 * This implementation bridges the Axon Server persistent stream with the Axon Framework 5 API.
 * <p>
 * <b>Filtering Support:</b>
 * <p>
 * The {@link #subscribe(EventCriteria, BiFunction)} method supports filtering by:
 * <ul>
 *   <li><b>Event type</b> - using {@link EventMessage#type()}</li>
 *   <li><b>Aggregate-based tags</b> - for legacy aggregate events, a {@link Tag} is automatically constructed
 *       from the aggregate type (as key) and aggregate identifier (as value) stored in the event's
 *       {@link org.axonframework.messaging.core.Context Context}</li>
 * </ul>
 * <p>
 * For aggregate-based events (legacy approach), aggregate information is available in the event's
 * {@link org.axonframework.messaging.core.Context Context} via
 * {@link org.axonframework.messaging.core.LegacyResources LegacyResources} keys:
 * <ul>
 *   <li>{@code LegacyResources.AGGREGATE_IDENTIFIER_KEY} - the aggregate identifier</li>
 *   <li>{@code LegacyResources.AGGREGATE_TYPE_KEY} - the aggregate type</li>
 *   <li>{@code LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY} - the aggregate sequence number</li>
 * </ul>
 * <p>
 * <b>Note:</b> Generic tag-based filtering (tags not derived from aggregates) is <b>not supported</b> because
 * persistent stream events from Axon Server do not expose arbitrary tags on the client side after conversion.
 * For such filtering, apply filters at the Axon Server level using persistent stream configuration.
 *
 * @author Marc Gathier
 * @author Mateusz Nowak
 * @since 4.10.0
 */
public class PersistentStreamMessageSource implements SubscribableEventSource {

    private final PersistentStreamConnection persistentStreamConnection;
    private final String name;

    private BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> consumer = NO_OP_CONSUMER;
    private static final BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> NO_OP_CONSUMER = (events, context) -> CompletableFuture.completedFuture(
            null);

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
        this.name = name;
        persistentStreamConnection = new PersistentStreamConnection(name,
                                                                    configuration,
                                                                    persistentStreamProperties,
                                                                    scheduler,
                                                                    batchSize,
                                                                    context,
                                                                    AggregateEventConverter.INSTANCE);
    }

    @Override
    public Registration subscribe(
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer
    ) {
        synchronized (this) {
            boolean noConsumer = this.consumer.equals(NO_OP_CONSUMER);
            if (noConsumer) {
                persistentStreamConnection.open(entries -> {
                    List<EventMessage> events = entries.stream()
                                                       .map(MessageStream.Entry::message)
                                                       .toList();

                    // Only call consumer if there are events
                    if (!events.isEmpty()) {
                        // Pass null ProcessingContext - the SubscribingEventProcessor will create its own UnitOfWork.
                        // fixme: We lose TrackingToken so we cannot determine if it's replay or not!
                        FutureUtils.joinAndUnwrap(eventsBatchConsumer.apply(events, null));
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
     * This implementation filters events by their {@link EventMessage#type() type} and, for aggregate-based events,
     * constructs tags from the aggregate type and identifier stored in the event's {@link org.axonframework.messaging.core.Context Context}.
     * <p>
     * For legacy aggregate-based events, a {@link Tag} is created with the aggregate type as key and aggregate
     * identifier as value (e.g., {@code Tag("OrderAggregate", "order-123")}). This enables filtering by aggregate
     * when using {@link EventCriteria#havingTags}.
     */
    @Override
    public Registration subscribe(
            @Nonnull EventCriteria criteria,
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer
    ) {
        Objects.requireNonNull(criteria, "EventCriteria must not be null");
        Objects.requireNonNull(eventsBatchConsumer, "eventsBatchConsumer must not be null");

        synchronized (this) {
            boolean noConsumer = this.consumer.equals(NO_OP_CONSUMER);
            if (noConsumer) {
                persistentStreamConnection.open(entries -> {
                    // Filter entries by criteria, using aggregate info from Context to build tags
                    List<EventMessage> filteredEvents = entries.stream()
                                                               .filter(entry -> {
                                                                   String type = entry.getResource(LegacyResources.AGGREGATE_TYPE_KEY);
                                                                   String identifier = entry.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY);
                                                                   Set<Tag> tags = (type == null || identifier == null)
                                                                           ? Set.of()
                                                                           : Set.of(new Tag(type, identifier));

                                                                   return criteria.matches(entry.message().type().qualifiedName(), tags);
                                                               })
                                                               .map(MessageStream.Entry::message)
                                                               .toList();

                    if (!filteredEvents.isEmpty()) {
                        // fixme: We lose TrackingToken so we cannot determine if it's replay or not!
                        FutureUtils.joinAndUnwrap(eventsBatchConsumer.apply(filteredEvents, null));
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
}
