/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 * Implementation of the {@link EventBus} that supports streaming of events via {@link #openStream(TrackingToken)} but
 * only of the most recently published events as it is not backed by a cache or event storage.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleEventBus extends AbstractEventBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBus.class);

    private static final int DEFAULT_QUEUE_CAPACITY = Integer.MAX_VALUE;

    private final Collection<EventConsumer> eventStreams = new CopyOnWriteArraySet<>();

    private final int queueCapacity;

    /**
     * Instantiate a {@link SimpleEventBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleEventBus} instance
     */
    protected SimpleEventBus(Builder builder) {
        super(builder);
        this.queueCapacity = builder.queueCapacity;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SimpleEventBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor} and the {@code queueCapacity} to
     * {@link Integer#MAX_VALUE}.
     *
     * @return a Builder to be able to create a {@link SimpleEventBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void afterCommit(List<? extends EventMessage<?>> events) {
        eventStreams.forEach(reader -> reader.addEvents(events));
    }

    /**
     * This implementation only returns a stream if the specified {@code trackingToken} is {@code null}. Otherwise it
     * will throw a {@link UnsupportedOperationException}.
     * <p>
     * The returned stream will receive all events published on the bus from the moment of opening the stream. Note that
     * the tracking tokens of {@link TrackedEventMessage TrackedEventMessages} in the stream will be {@code null};
     * <p>
     * {@inheritDoc}
     */
    @Override
    public TrackingEventStream openStream(TrackingToken trackingToken) {
        if (trackingToken != null) {
            throw new UnsupportedOperationException("The simple event bus does not support non-null tracking tokens");
        }
        EventConsumer eventStream = new EventConsumer(queueCapacity);
        eventStreams.add(eventStream);
        return eventStream;
    }

    /**
     * Builder class to instantiate a {@link SimpleEventBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor} and the {@code queueCapacity} to
     * {@link Integer#MAX_VALUE}.
     */
    public static class Builder extends AbstractEventBus.Builder {

        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

        @Override
        public Builder messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        /**
         * Sets the {@code queueCapacity}, specifying the maximum number of events to hold in memory for event tracking.
         * Must be a positive number. Defaults to {@link Integer#MAX_VALUE}.
         *
         * @param queueCapacity an {@code int} specifying the maximum number of events to hold in memory for event
         *                      tracking
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder queueCapacity(int queueCapacity) {
            assertThat(queueCapacity, capacity -> capacity > 0, "The queueCapacity must be a positive number");
            this.queueCapacity = queueCapacity;
            return this;
        }

        /**
         * Initializes a {@link SimpleEventBus} as specified through this Builder.
         *
         * @return a {@link SimpleEventBus} as specified through this Builder
         */
        public SimpleEventBus build() {
            return new SimpleEventBus(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
        }
    }

    private class EventConsumer implements TrackingEventStream {

        private final BlockingQueue<TrackedEventMessage<?>> eventQueue;
        private TrackedEventMessage<?> peekEvent;

        private EventConsumer(int queueCapacity) {
            eventQueue = new LinkedBlockingQueue<>(queueCapacity);
        }

        private void addEvents(List<? extends EventMessage<?>> events) {
            // Add one by one because bulk operations on LinkedBlockingQueues are not thread-safe
            events.forEach(eventMessage -> {
                try {
                    eventQueue.put(asTrackedEventMessage(eventMessage, null));
                } catch (InterruptedException e) {
                    logger.warn("Event producer thread was interrupted. Shutting down.", e);
                    Thread.currentThread().interrupt();
                }
            });
        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            return Optional.ofNullable(peekEvent == null && !hasNextAvailable() ? null : peekEvent);
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) {
            try {
                return peekEvent != null || (peekEvent = eventQueue.poll(timeout, unit)) != null;
            } catch (InterruptedException e) {
                logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() {
            try {
                return peekEvent == null ? eventQueue.take() : peekEvent;
            } catch (InterruptedException e) {
                logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
                Thread.currentThread().interrupt();
                return null;
            } finally {
                peekEvent = null;
            }
        }

        @Override
        public void close() {
            eventStreams.remove(this);
        }
    }
}
