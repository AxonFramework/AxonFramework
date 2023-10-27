/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.EventStreamUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The DomainEventStream represents a stream of historical events published by an Aggregate. The order of events in this
 * stream must represent the actual chronological order in which the events happened. A DomainEventStream may provide
 * access to all events (from the first to the most recent) or any subset of these.
 *
 * @author Rene de Waele
 */
public interface DomainEventStream extends Iterator<DomainEventMessage<?>> {

    /**
     * Create a new DomainEventStream with events obtained from the given {@code stream}.
     *
     * @param stream                 Stream that serves as a source of events in the resulting DomainEventStream
     * @param sequenceNumberSupplier supplier of the sequence number of the last used upstream event entry
     * @return A DomainEventStream containing all events contained in the stream
     */
    static DomainEventStream of(Stream<? extends DomainEventMessage<?>> stream, Supplier<Long> sequenceNumberSupplier) {
        Objects.requireNonNull(stream);
        return new IteratorBackedDomainEventStream(stream.iterator()) {
            @Override
            public Long getLastSequenceNumber() {
                return sequenceNumberSupplier.get();
            }
        };
    }

    /**
     * Create a new DomainEventStream with events obtained from the given {@code stream}.
     *
     * @param stream Stream that serves as a source of events in the resulting DomainEventStream
     * @return A DomainEventStream containing all events contained in the stream
     */
    static DomainEventStream of(Stream<? extends DomainEventMessage<?>> stream) {
        return new IteratorBackedDomainEventStream(stream.iterator());
    }

    /**
     * Create an empty DomainEventStream.
     *
     * @return A DomainEventStream containing no events
     */
    static DomainEventStream empty() {
        return DomainEventStream.of();
    }

    /**
     * Create a new DomainEventStream containing only the given {@code event}.
     *
     * @param event The event to add to the resulting DomainEventStream
     * @return A DomainEventStream consisting of only the given event
     */
    static DomainEventStream of(DomainEventMessage<?> event) {
        Objects.requireNonNull(event);
        return new DomainEventStream() {
            private boolean hasNext = true;

            @Override
            public DomainEventMessage<?> peek() {
                if (hasNext) {
                    return event;
                }
                throw new NoSuchElementException();
            }

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public DomainEventMessage<?> next() {
                if (hasNext) {
                    hasNext = false;
                    return event;
                }
                throw new NoSuchElementException();
            }

            @Override
            public Long getLastSequenceNumber() {
                return event.getSequenceNumber();
            }
        };
    }

    /**
     * Create a new DomainEventStream from the given {@code events}.
     *
     * @param events Events to add to the resulting DomainEventStream
     * @return A DomainEventStream consisting of all given events
     */
    static DomainEventStream of(DomainEventMessage<?>... events) {
        return DomainEventStream.of(Arrays.asList(events));
    }

    /**
     * Create a new DomainEventStream with events obtained from the given {@code list}.
     *
     * @param list list that serves as a source of events in the resulting DomainEventStream
     * @return A DomainEventStream containing all events returned by the list
     */
    static DomainEventStream of(List<? extends DomainEventMessage<?>> list) {
        return list.isEmpty() ? of(Stream.empty(), () -> null) :
                of(list.stream(), () -> list.isEmpty() ? null : list.get(list.size() - 1).getSequenceNumber());
    }

    /**
     * Concatenate two DomainEventStreams. In the resulting stream events from stream {@code a} will be followed by
     * events from stream {@code b}.
     *
     * @param a The first stream
     * @param b The second stream that will follow the first stream
     * @return A concatenation of stream a and b
     */
    static DomainEventStream concat(DomainEventStream a, DomainEventStream b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        return new ConcatenatingDomainEventStream(a, b);
    }

    /**
     * Returns a stream that provides the items of this stream that match the given {@code filter}.
     *
     * @param filter The filter to apply to the stream
     * @return A filtered version of this stream 
     */
    default DomainEventStream filter(Predicate<? super DomainEventMessage<?>> filter) {
        Objects.requireNonNull(filter);
        return new FilteringDomainEventStream(this, filter);
    }
    
    /**
     * Returns {@code true} if the stream has more events, meaning that a call to {@code next()} will not
     * result in an exception. If a call to this method returns {@code false}, there is no guarantee about the
     * result of a consecutive call to {@code next()}
     *
     * @return {@code true} if the stream contains more events.
     */
    @Override
    boolean hasNext();

    /**
     * Returns the next events in the stream, if available. Use {@code hasNext()} to obtain a guarantee about the
     * availability of any next event. Each call to {@code next()} will forward the pointer to the next event in
     * the stream.
     * <p/>
     * If the pointer has reached the end of the stream, the behavior of this method is undefined. It could either
     * return {@code null}, or throw an exception, depending on the actual implementation. Use {@link #hasNext()}
     * to confirm the existence of elements after the current pointer.
     *
     * @return the next event in the stream.
     */
    @Override
    DomainEventMessage<?> next();

    /**
     * Returns the next events in the stream, if available, without moving the pointer forward. Hence, a call to {@link
     * #next()} will return the same event as a call to {@code peek()}. Use {@code hasNext()} to obtain a
     * guarantee about the availability of any next event.
     * <p/>
     * If the pointer has reached the end of the stream, the behavior of this method is undefined. It could either
     * return {@code null}, or throw an exception, depending on the actual implementation. Use {@link #hasNext()}
     * to confirm the existence of elements after the current pointer.
     *
     * @return the next event in the stream.
     */
    DomainEventMessage<?> peek();

    /**
     * Get the highest known sequence number in the upstream event entry stream. Note that, as result of upcasting it is
     * possible that the last event in this stream has a lower sequence number than that returned by this method.
     * <p>
     * To get the highest absolute sequence number of the underlying event entry stream make sure to iterate over all
     * elements in the stream before calling this method.
     * <p>
     * If the stream is empty this method returns {@code null}.
     *
     * @return the sequence number of the last known upstream event entry
     */
    Long getLastSequenceNumber();

    @Override
    default void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns this DomainEventStream as a {@link Stream} of DomainEventMessages. Note that the returned Stream will
     * start at the current position of the DomainEventStream.
     * <p>
     * Note that iterating over the returned Stream may affect this DomainEventStream and vice versa. It is therefore
     * not recommended to use this DomainEventStream after invoking this method.
     *
     * @return This DomainEventStream as a Stream of event messages
     */
    default Stream<? extends DomainEventMessage<?>> asStream() {
        return EventStreamUtils.asStream(this);
    }

}
