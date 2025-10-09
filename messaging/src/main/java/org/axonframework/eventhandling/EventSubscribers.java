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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiFunction;

/**
 * Internal utility class for managing event subscribers.
 * <p>
 * This class provides thread-safe subscription management and notification of subscribers when events are published. It
 * is designed to be reused across different components in the framework that need to support event subscription.
 * <p>
 * The subscription mechanism is thread-safe, using {@link CopyOnWriteArraySet} to allow concurrent modifications and
 * iterations. Duplicate subscriptions are automatically prevented.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class EventSubscribers implements DescribableComponent {

    private static final Logger logger = LoggerFactory.getLogger(EventSubscribers.class);

    private final Set<BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>>> subscribers = new CopyOnWriteArraySet<>();

    /**
     * Subscribes the given {@code eventsBatchConsumer} to receive notifications when events are published.
     * <p>
     * If the consumer is already subscribed, it will not be added again and an info message will be logged.
     *
     * @param eventsBatchConsumer The consumer to subscribe for event notifications.
     * @return A {@link Registration} that can be used to unsubscribe the consumer.
     */
    public Registration subscribe(
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer
    ) {
        if (this.subscribers.add(eventsBatchConsumer)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Event subscriber [{}] subscribed successfully", eventsBatchConsumer);
            }
        } else {
            logger.info("Event subscriber [{}] not added. It was already subscribed", eventsBatchConsumer);
        }
        return () -> {
            if (subscribers.remove(eventsBatchConsumer)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Event subscriber {} unsubscribed successfully", eventsBatchConsumer);
                }
                return true;
            } else {
                logger.info("Event subscriber {} not removed. It was already unsubscribed", eventsBatchConsumer);
                return false;
            }
        };
    }

    /**
     * Notifies all subscribers with the given events and processing context.
     *
     * @param events  The list of events to notify subscribers about.
     * @param context The {@link ProcessingContext} associated with the events, may be {@code null}.
     */
    public void notifySubscribers(
            @Nonnull List<? extends EventMessage> events,
            @Nullable ProcessingContext context
    ) {
        subscribers.forEach(subscriber -> subscriber.apply(events, context));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("subscribers", subscribers);
    }
}