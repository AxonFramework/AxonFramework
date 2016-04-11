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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * @author Rene de Waele
 */
public class EmbeddedEventStore extends AbstractEventStore {

    private static final int DEFAULT_BACKLOG_LENGTH = 1024;
    private final EventCache eventCache;

    public EmbeddedEventStore(EventStorageEngine storageEngine) {
        super(storageEngine);
        eventCache = new EventCache(DEFAULT_BACKLOG_LENGTH);
    }

    public EmbeddedEventStore(PublicationStrategy publicationStrategy, EventStorageEngine storageEngine) {
        this(publicationStrategy, storageEngine, DEFAULT_BACKLOG_LENGTH);
    }

    public EmbeddedEventStore(PublicationStrategy publicationStrategy, EventStorageEngine storageEngine,
                              int backlogLength) {
        super(publicationStrategy, storageEngine);
        eventCache = new EventCache(backlogLength);
    }

    @Override
    protected void afterCommit(List<EventMessage<?>> events) {
        //todo wake up waiting polling stream
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken) {
        //todo
        return null;
    }

    private class Consumer {
        private Node head, tail;

    }

    private static class Node {
        private final TrackedEventMessage<?> event;
        private final AtomicReference<TrackedEventMessage<?>> next = new AtomicReference<>();

        private Node(TrackedEventMessage<?> event) {
            this.event = event;
        }
    }

    private static class EventCache extends LinkedHashMap<TrackingToken, TrackedEventMessage<?>> {
        private final int maxSize;
        private int size;

        private EventCache(int maxSize) {
            super(maxSize);
            this.maxSize = maxSize;
        }

        private void add(TrackedEventMessage<?> event) {
            size++;
            put(event.trackingToken(), event);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<TrackingToken, TrackedEventMessage<?>> eldest) {
            return size > maxSize;
        }
    }
}
