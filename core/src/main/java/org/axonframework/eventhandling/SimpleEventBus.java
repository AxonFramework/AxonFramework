/*
 * Copyright (c) 2010-2015. Axon Framework
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

import org.axonframework.eventstore.TrackingToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.axonframework.eventstore.EventUtils.asTrackedEventMessage;

/**
 * Implementation of the {@link EventBus} that supports streaming of events via {@link #readEvents(TrackingToken)} but
 * only of the most recently published events as it is not backed by a cache or event storage.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleEventBus extends AbstractEventBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBus.class);

    private static final int DEFAULT_QUEUE_CAPACITY = Integer.MAX_VALUE;

    private final Collection<EventStreamSpliterator> eventReaders = new CopyOnWriteArraySet<>();
    private final int queueCapacity;

    public SimpleEventBus() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    public SimpleEventBus(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    @Override
    protected void afterCommit(List<EventMessage<?>> events) {
        eventReaders.forEach(reader -> reader.addEvents(events));
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
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken) {
        if (trackingToken != null) {
            throw new UnsupportedOperationException("The simple event bus does not support non-null tracking tokens");
        }
        EventStreamSpliterator spliterator = new EventStreamSpliterator(queueCapacity);
        eventReaders.add(spliterator);
        Stream<? extends TrackedEventMessage<?>> stream = StreamSupport.stream(spliterator, false);
        stream.onClose(() -> eventReaders.remove(spliterator));
        return stream;
    }

    private class EventStreamSpliterator extends Spliterators.AbstractSpliterator<TrackedEventMessage<?>> {
        private final BlockingQueue<TrackedEventMessage<?>> eventQueue;

        private EventStreamSpliterator(int queueCapacity) {
            super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT | CONCURRENT);
            eventQueue = new LinkedBlockingQueue<>(queueCapacity);
        }

        private void addEvents(List<EventMessage<?>> events) {
            //add one by one because bulk operations on LinkedBlockingQueues are not thread-safe
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
        public boolean tryAdvance(Consumer<? super TrackedEventMessage<?>> action) {
            try {
                action.accept(eventQueue.take());
            } catch (InterruptedException e) {
                logger.warn("Thread was interrupted while waiting for the next item on the queue", e);
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        }
    }
}
