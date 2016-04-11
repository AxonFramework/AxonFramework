/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.PublicationStrategy;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Rene de Waele
 */
public class StupidEventStore extends AbstractEventStore {

    private static final long DEFAULT_POLLING_INTERVAL = 1000L;
    private static final Logger logger = LoggerFactory.getLogger(StupidEventStore.class);

    private long pollingInterval = DEFAULT_POLLING_INTERVAL;

    public StupidEventStore(EventStorageEngine storageEngine) {
        super(storageEngine);
    }

    public StupidEventStore(PublicationStrategy publicationStrategy, EventStorageEngine storageEngine) {
        super(publicationStrategy, storageEngine);
    }

    @Override
    protected void afterCommit(List<EventMessage<?>> events) {
        //todo
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken) {
        EventStreamSpliterator spliterator = new EventStreamSpliterator(trackingToken);
        return StreamSupport.stream(spliterator, false);
    }

    public void setPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    private class EventStreamSpliterator extends Spliterators.AbstractSpliterator<TrackedEventMessage<?>> {

        //todo close event stream when downstream closes

        private Spliterator<? extends TrackedEventMessage<?>> delegate;
        private final Consumer<TrackedEventMessage<?>> eventConsumer = event -> lastToken = event.trackingToken();
        private TrackingToken lastToken;

        private EventStreamSpliterator(TrackingToken trackingToken) {
            this(storageEngine().readEvents(trackingToken).spliterator(), trackingToken);
        }

        private EventStreamSpliterator(Spliterator<? extends TrackedEventMessage<?>> delegate, TrackingToken startToken) {
            super(delegate.estimateSize(), delegate.characteristics());
            this.delegate = delegate;
            this.lastToken = startToken;
        }

        @Override
        public boolean tryAdvance(Consumer<? super TrackedEventMessage<?>> action) {
            if (!delegate.tryAdvance(eventConsumer.andThen(action))) {
                try {
                    Thread.sleep(pollingInterval);
                    delegate = storageEngine().readEvents(lastToken).spliterator();
                    return tryAdvance(action);
                } catch (InterruptedException e) {
                    logger.warn("Reader thread interrupted", e);
                    return false;
                }
            }
            return true;
        }
    }
}
