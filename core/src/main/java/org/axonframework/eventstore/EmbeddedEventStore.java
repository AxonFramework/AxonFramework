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

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.io.IOUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * @author Rene de Waele
 */
public class EmbeddedEventStore extends AbstractEventStore {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedEventStore.class);
    private static final ThreadGroup THREAD_GROUP = new ThreadGroup(EmbeddedEventStore.class.getSimpleName());

    private final Lock consumerLock = new ReentrantLock();
    private final Condition consumableEventsCondition = consumerLock.newCondition();
    private final Set<EventConsumer> tailingConsumers = new CopyOnWriteArraySet<>();
    private final EventProducer producer;
    private final long cleanupDelayMillis;
    private final ThreadFactory threadFactory;
    private final ScheduledExecutorService cleanupService;

    private volatile Node oldest;

    public EmbeddedEventStore(EventStorageEngine storageEngine) {
        this(storageEngine, 10000, 1000L, 10000L, TimeUnit.MILLISECONDS);
    }

    public EmbeddedEventStore(EventStorageEngine storageEngine, int cachedEvents, long fetchDelay, long cleanupDelay,
                              TimeUnit timeUnit) {
        super(storageEngine);
        threadFactory = new AxonThreadFactory(THREAD_GROUP);
        cleanupService = Executors.newScheduledThreadPool(1, threadFactory);
        producer = new EventProducer(timeUnit.toNanos(fetchDelay), cachedEvents);
        cleanupDelayMillis = timeUnit.toMillis(cleanupDelay);
    }

    @PostConstruct
    public void initialize() {
        threadFactory.newThread(producer::tryFetch).start();
        cleanupService
                .scheduleWithFixedDelay(new Cleaner(), cleanupDelayMillis, cleanupDelayMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        tailingConsumers.forEach(IOUtils::closeQuietly);
        IOUtils.closeQuietly(producer);
        cleanupService.shutdownNow();
    }

    @Override
    protected void afterCommit(List<? extends EventMessage<?>> events) {
        producer.fetchIfWaiting();
    }

    @Override
    public TrackingEventStream streamEvents(TrackingToken trackingToken) {
        Node node = findNode(trackingToken);
        EventConsumer eventConsumer;
        if (node != null) {
            eventConsumer = new EventConsumer(node);
            tailingConsumers.add(eventConsumer);
        } else {
            eventConsumer = new EventConsumer(trackingToken);
        }
        return eventConsumer;
    }

    private Node findNode(TrackingToken trackingToken) {
        Node oldest = this.oldest;
        return oldest == null ? null : oldest.find(trackingToken);
    }

    private class EventProducer implements AutoCloseable {
        private final Lock lock = new ReentrantLock();
        private final Condition dataAvailableCondition = lock.newCondition();
        private final long fetchDelayNanos;
        private final int cachedEvents;
        private volatile boolean fetching, shouldFetch;
        private Stream<? extends TrackedEventMessage<?>> eventStream;
        private Node newest;

        private EventProducer(long fetchDelayNanos, int cachedEvents) {
            this.fetchDelayNanos = fetchDelayNanos;
            this.cachedEvents = cachedEvents;
        }

        private void fetchIfWaiting() {
            shouldFetch = true;
            if (!fetching) {
                lock.lock();
                try {
                    dataAvailableCondition.signal();
                } finally {
                    lock.unlock();
                }
            }
        }

        private void tryFetch() {
            shouldFetch = true;
            fetching = true;
            while (shouldFetch) {
                shouldFetch = false;
                if (!tailingConsumers.isEmpty()) {
                    (eventStream = storageEngine().readEvents(lastToken(), true)).forEach(event -> {
                        Node node = new Node(nextIndex(), event);
                        if (newest != null) {
                            newest.next = node;
                        }
                        newest = node;
                        if (oldest == null) {
                            oldest = node;
                        }
                        notifyConsumers();
                    });
                    trimCache();
                }
            }
            fetching = false;
            if (tailingConsumers.isEmpty()) {
                newest = null;
            }
            delayedFetch();
        }

        private TrackingToken lastToken() {
            if (newest == null) {
                return tailingConsumers.stream().map(EventConsumer::lastToken)
                        .min(Comparator.nullsFirst(Comparator.naturalOrder())).orElse(null);
            } else {
                return newest.event.trackingToken();
            }
        }

        private long nextIndex() {
            return newest == null ? 0 : newest.index + 1;
        }

        private void delayedFetch() {
            lock.lock();
            try {
                dataAvailableCondition.awaitNanos(fetchDelayNanos);
            } catch (InterruptedException e) {
                logger.warn("Producer thread was interrupted. Shutting down event store.", e);
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }
            tryFetch();
        }

        private void notifyConsumers() {
            consumerLock.lock();
            try {
                consumableEventsCondition.signalAll();
            } finally {
                consumerLock.unlock();
            }
        }

        private void trimCache() {
            while (newest != null && newest.index - oldest.index > cachedEvents) {
                oldest = oldest.next;
            }
        }

        @Override
        public void close() {
            oldest = null;
            if (eventStream != null) {
                eventStream.close();
            }
        }
    }

    private class EventConsumer implements TrackingEventStream {
        private Stream<? extends TrackedEventMessage<?>> privateStream;
        private Iterator<? extends TrackedEventMessage<?>> privateIterator;
        private volatile TrackingToken lastToken;
        private volatile Node lastNode;
        private TrackedEventMessage<?> peekedEvent;

        private EventConsumer(Node lastNode) {
            this(lastNode.event.trackingToken());
            this.lastNode = lastNode;
        }

        private EventConsumer(TrackingToken startToken) {
            this.lastToken = startToken;
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            return peekedEvent != null || (peekedEvent = peek(timeout, unit)) != null;
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            while (peekedEvent == null) {
                peekedEvent = peek(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
            TrackedEventMessage<?> result = peekedEvent;
            peekedEvent = null;
            return result;
        }

        private TrackedEventMessage<?> peek(int timeout, TimeUnit timeUnit) throws InterruptedException {
            if (tailingConsumers.contains(this)) {
                Node nextNode;
                if ((nextNode = nextNode()) == null && timeout > 0) {
                    consumerLock.lock();
                    try {
                        if (!tailingConsumers.contains(this)) {
                            return peek(timeout, timeUnit); //consumer was removed by cleaner: open a new private stream
                        }
                        consumableEventsCondition.await(timeout, timeUnit);
                        if (isBehindCache()) { //consumer is behind the cache: open a new private stream
                            tailingConsumers.remove(this);
                            return peek(timeout, timeUnit);
                        }
                        nextNode = nextNode();
                    } finally {
                        consumerLock.unlock();
                    }
                }
                if (nextNode != null) {
                    lastNode = nextNode;
                    lastToken = peekedEvent.trackingToken();
                    return nextNode.event;
                } else {
                    return null;
                }
            } else {
                if (privateIterator == null) {
                    privateStream = storageEngine().readEvents(lastToken, false);
                    privateIterator = privateStream.iterator();
                }
                if (privateIterator.hasNext()) {
                    TrackedEventMessage<?> nextEvent = privateIterator.next();
                    lastToken = nextEvent.trackingToken();
                    return nextEvent;
                } else {
                    closePrivateStream();
                    tailingConsumers.add(this);
                    return timeout > 0 ? peek(timeout, timeUnit) : null;
                }
            }
        }

        private Node nextNode() {
            Node current;
            if ((current = lastNode) == null && (current = lastNode = findNode(lastToken)) == null) {
                return null;
            }
            return current.next;
        }

        private boolean isBehindCache() {
            return lastToken == null || (oldest != null && oldest.event.trackingToken().isAfter(lastToken) &&
                    (lastNode == null || lastNode.next == null));
        }

        private TrackingToken lastToken() {
            return lastToken;
        }

        @Override
        public void close() {
            closePrivateStream();
            tailingConsumers.remove(this);
        }

        private void closePrivateStream() {
            Optional.ofNullable(privateStream).ifPresent(stream -> {
                privateStream = null;
                privateIterator = null;
                stream.close();
            });
        }
    }

    private class Cleaner implements Runnable {
        @Override
        public void run() {
            Node currentOldest = oldest;
            if (currentOldest == null) {
                return;
            }
            Iterator<EventConsumer> iterator = tailingConsumers.iterator();
            while (iterator.hasNext()) {
                EventConsumer consumer = iterator.next();
                Node lastNode = consumer.lastNode;
                if (lastNode != null && currentOldest.index > lastNode.index) {
                    iterator.remove();
                    consumer.lastNode = null; //make old nodes garbage collectable
                }
            }
        }
    }

    private static class Node {
        private final long index;
        private final TrackedEventMessage<?> event;
        private volatile Node next;

        private Node(long index, TrackedEventMessage<?> event) {
            this.index = index;
            this.event = event;
        }

        private Node find(TrackingToken trackingToken) {
            if (event.trackingToken().isAfter(trackingToken)) {
                return null;
            }
            Node node = this;
            while (node != null && !node.event.trackingToken().equals(trackingToken)) {
                node = node.next;
            }
            return node;
        }
    }
}
