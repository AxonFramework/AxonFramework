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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Interface for a source of {@link EventMessage EventMessages} to which event processors can subscribe.
 * <p>
 * Provides functionality to {@link #subscribe(BiFunction) subscribe} event batch consumers to receive
 * {@link EventMessage events} published to this source. When subscribed, consumers will receive all events published to
 * this source since the subscription.
 * <p>
 * Implementations may support client-side filtering using {@link EventCriteria} through the
 * {@link #subscribe(EventCriteria, BiFunction)} method. By default, this method filters events based on the criteria
 * before passing them to the consumer. Implementations can override this behavior to optimize filtering at the source.
 * <p>
 * <b>Filtering Limitations:</b>
 * <p>
 * The default implementation of {@link #subscribe(EventCriteria, BiFunction)} only supports filtering by event
 * {@link EventMessage#type() type}. Tag-based filtering is <b>not supported</b> at the client side because
 * {@link EventMessage} does not expose tags directly. Tags are typically resolved and associated with events
 * during publishing and stored server-side.
 * <p>
 * For aggregate-based events (legacy approach), aggregate information such as aggregate identifier, type, and
 * sequence number may be available in the event's processing context via {@code LegacyResources}, but these
 * are not automatically used for tag-based filtering.
 * <p>
 * To enable tag-based filtering, implementations should either:
 * <ul>
 *     <li>Apply filtering at the server/source level where tags are available</li>
 *     <li>Override {@link #subscribe(EventCriteria, BiFunction)} with access to tag information</li>
 * </ul>
 * <p>
 * This interface is the replacement for the deprecated {@code SubscribableMessageSource},
 * focusing specifically on event message handling.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface SubscribableEventSource {

    /**
     * Subscribe the given {@code eventsBatchConsumer} to this event source with the specified filtering criteria.
     * When subscribed, it will receive only events that match the given {@code criteria}.
     * <p>
     * If the given {@code eventsBatchConsumer} is already subscribed, nothing happens.
     * <p>
     * The default implementation wraps the consumer to filter events based on the criteria before passing them
     * to the underlying {@link #subscribe(BiFunction)} method. Implementations may override this to apply
     * filtering at the source level for better efficiency.
     * <p>
     * <b>Note on {@link ProcessingContext}:</b> The {@link ProcessingContext} parameter passed to the consumer may be
     * {@code null}. When {@code null}, it is the responsibility of the registered {@code eventsBatchConsumer} to create
     * an appropriate {@link ProcessingContext} as needed for processing the events.
     * <p>
     * <b>Note on filtering:</b> The default implementation filters events by their
     * {@link EventMessage#type() type} only. Tags are not available from the {@link EventMessage} directly
     * and filtering by tags requires implementation-specific support.
     *
     * @param criteria            The {@link EventCriteria} to filter events. Only events matching the criteria
     *                            will be passed to the consumer.
     * @param eventsBatchConsumer The event batches consumer to subscribe.
     * @return A {@link Registration} handle to unsubscribe the {@code eventsBatchConsumer}. When unsubscribed, it will
     * no longer receive events.
     */
    default Registration subscribe(@Nonnull EventCriteria criteria,
                                   @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
        // Default implementation filters by type only (tags not available from EventMessage)
        return subscribe((events, context) -> {
            List<? extends EventMessage> filteredEvents = events.stream()
                                                                .filter(event -> criteria.matches(event.type().qualifiedName(), java.util.Collections.emptySet()))
                                                                .toList();
            if (filteredEvents.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return eventsBatchConsumer.apply(filteredEvents, context);
        });
    }

    /**
     * Subscribe the given {@code eventsBatchConsumer} to this event source. When subscribed, it will receive all events
     * published to this source since the subscription.
     * <p>
     * If the given {@code eventsBatchConsumer} is already subscribed, nothing happens.
     * <p>
     * <b>Note on {@link ProcessingContext}:</b> The {@link ProcessingContext} parameter passed to the consumer may be
     * {@code null}. When {@code null}, it is the responsibility of the registered {@code eventsBatchConsumer} to create
     * an appropriate {@link ProcessingContext} as needed for processing the events.
     *
     * @param eventsBatchConsumer The event batches consumer to subscribe.
     * @return A {@link Registration} handle to unsubscribe the {@code eventsBatchConsumer}. When unsubscribed, it will
     * no longer receive events.
     */
    Registration subscribe(@Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer);
}
