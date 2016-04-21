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
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Comparator.comparing;

/**
 * @author Rene de Waele
 */
public class EmbeddedEventStore extends AbstractEventStore {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedEventStore.class);
    private static final ThreadGroup THREAD_GROUP = new ThreadGroup(EmbeddedEventStore.class.getSimpleName());

    private final Lock consumerLock = new ReentrantLock();
    private final Condition consumableEventsCondition = consumerLock.newCondition();
    private final Set<EventConsumer> tailingConsumers = new CopyOnWriteArraySet<>();
    private final Producer producer;
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
        producer = new Producer(timeUnit.toNanos(fetchDelay), cachedEvents);
        cleanupDelayMillis = timeUnit.toMillis(cleanupDelay);
    }

    @PostConstruct
    public void initialize() {
        threadFactory.newThread(producer::tryFetch);
        cleanupService
                .scheduleWithFixedDelay(new Cleaner(), cleanupDelayMillis, cleanupDelayMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        cleanupService.shutdownNow();
        oldest = null;
    }

    @Override
    protected void afterCommit(List<EventMessage<?>> events) {
        producer.fetchIfWaiting();
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken) {
        Node node = findNode(trackingToken);
        EventConsumer eventConsumer;
        if (node != null) {
            eventConsumer = new EventConsumer(node);
            tailingConsumers.add(eventConsumer);
        } else {
            eventConsumer = new EventConsumer(trackingToken);
        }
        return StreamSupport.stream(eventConsumer, false).onClose(eventConsumer::closePrivateStream);
    }

    private Node findNode(TrackingToken trackingToken) {
        Node oldest = this.oldest;
        return oldest == null ? null : oldest.find(trackingToken);
    }

    private class Producer {
        private final Lock lock = new ReentrantLock();
        private final Condition dataAvailableCondition = lock.newCondition();
        private final long fetchDelayNanos;
        private final int cachedEvents;
        private volatile boolean fetching, shouldFetch;
        private Node newest;

        private Producer(long fetchDelayNanos, int cachedEvents) {
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
                    if (newest == null) {
                        newest = tailingConsumers.stream().map(consumer -> consumer.lastMessage)
                                .min(comparing(TrackedEventMessage::trackingToken, Comparator.naturalOrder()))
                                .map(event -> new Node(0, event)).orElse(null);
                    }
                    storageEngine().readEvents(lastToken()).forEach(event -> {
                        Node node = new Node(nextIndex(), event);
                        newest.next = node;
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
            return newest == null ? null : newest.event.trackingToken();
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
    }

    private class EventConsumer extends Spliterators.AbstractSpliterator<TrackedEventMessage<?>> {
        private final TrackingToken startToken;
        private Stream<? extends TrackedEventMessage<?>> privateStream;
        private Spliterator<? extends TrackedEventMessage<?>> privateSpliterator;
        private volatile TrackedEventMessage<?> lastMessage;
        private volatile Node lastNode;

        private EventConsumer(Node lastNode) {
            this(lastNode.event.trackingToken());
            this.lastNode = lastNode;
            this.lastMessage = lastNode.event;
        }

        private EventConsumer(TrackingToken startToken) {
            super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT);
            this.startToken = startToken;
        }

        @Override
        public boolean tryAdvance(Consumer<? super TrackedEventMessage<?>> action) {
            if (tailingConsumers.contains(this)) {
                Node nextNode;
                if ((nextNode = nextNode()) == null) {
                    consumerLock.lock();
                    try {
                        while ((nextNode = nextNode()) == null) {
                            if (!tailingConsumers.contains(this)) {
                                return tryAdvance(action); //cleaner removed consumer: reopen a private stream
                            }
                            waitForEvents();
                        }
                    } catch (InterruptedException e) {
                        return false;
                    } finally {
                        consumerLock.unlock();
                    }
                }
                lastNode = nextNode;
                lastMessage = nextNode.event;
                action.accept(nextNode.event);
                return true;
            } else {
                TrackingToken lastToken = lastToken();
                if (privateSpliterator == null) {
                    privateStream = storageEngine().readEvents(lastToken);
                    privateSpliterator = privateStream.spliterator();
                }
                if (privateSpliterator.tryAdvance(event -> action.accept(lastMessage = event))) {
                    return true;
                } else if (lastMessage == null) {
                    consumerLock.lock();
                    try {
                        waitForEvents();
                    } catch (InterruptedException e) {
                        return false;
                    } finally {
                        consumerLock.unlock();
                    }
                    return tryAdvance(action);
                } else {
                    closePrivateStream();
                    tailingConsumers.add(this);
                    return tryAdvance(action);
                }
            }
        }

        private void waitForEvents() throws InterruptedException {
            try {
                consumableEventsCondition.await();
            } catch (InterruptedException e) {
                logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        private Node nextNode() {
            Node current;
            if ((current = lastNode) == null && (current = lastNode = findNode(lastToken())) == null) {
                return null;
            }
            return current.next;
        }


        private void closePrivateStream() {
            Optional.ofNullable(privateStream).ifPresent(stream -> {
                privateStream = null;
                privateSpliterator = null;
                stream.close();
            });
        }

        private TrackingToken lastToken() {
            return lastMessage == null ? startToken : lastMessage.trackingToken();
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

    private static class ConsumerEventStream implements EventStream {

        private final EventConsumer eventConsumer;

        public ConsumerEventStream(EventConsumer eventConsumer) {
            this.eventConsumer = eventConsumer;
        }

        @Override
        public Stream<? extends TrackedEventMessage<?>> all() {
            return StreamSupport.stream(eventConsumer, false).onClose(eventConsumer::closePrivateStream);
        }

        @Override
        public Stream<? extends TrackedEventMessage<?>> batch(int maxSize) {
            return null;
        }
    }
}
