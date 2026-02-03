/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link MessageStream} implementation that merges two underlying message streams into a single stream. Messages
 * from both streams are interleaved based on a provided {@link Comparator}, which determines the order in which
 * messages are consumed.
 * <p>
 * The merged stream is considered completed only when both underlying streams are completed. If either stream has an
 * error, that error is propagated through the merged stream, with errors from the first stream taking precedence.
 * However, when one of the underlying streams propagates an error, this stream can still be consumed from as long as
 * any other underlying streams provide messages. Clients that wish to abort consuming messages when there is an error
 * can {@link #close()} the stream once {@link #error()} returns a non-empty Optional.
 * <p>
 * When a message is consumed via {@link #next()}, the implementation peeks at the head of both streams and uses the
 * comparator to decide which message to consume. This ensures messages are returned in the desired order without
 * requiring either stream to be fully buffered.
 *
 * @param <M> The type of {@link Message} in the stream
 * @author Allard Buijze
 * @since 5.1.0
 */
public class MergedMessageStream<M extends Message> implements MessageStream<M> {

    private final MessageStream<M> first;
    private final MessageStream<M> second;
    private final Comparator<Entry<M>> comparator;

    /**
     * Constructs a {@code MergedMessageStream} that merges two message streams based on the given comparator.
     *
     * @param comparator The comparator used to determine the order in which messages from the two streams are
     *                   consumed. A result of {@code <= 0} means the message from the first stream is consumed first.
     * @param first      The first message stream to merge.
     * @param second     The second message stream to merge.
     */
    public MergedMessageStream(@NonNull Comparator<Entry<M>> comparator,
                               @NonNull MessageStream<M> first,
                               @NonNull MessageStream<M> second) {
        this.comparator = Objects.requireNonNull(comparator, "comparator must not be null");
        this.first = Objects.requireNonNull(first, "first must not be null");
        this.second = Objects.requireNonNull(second, "second must not be null");
    }

    @Override
    public Optional<Entry<M>> next() {
        Optional<Entry<M>> firstPeek = first.peek();
        Optional<Entry<M>> secondPeek = second.peek();
        if (firstPeek.isEmpty() && !second.isCompleted()) {
            return second.next();
        } else if (!first.isCompleted() && firstPeek.isPresent() &&
                (secondPeek.isEmpty() || comparator.compare(firstPeek.get(), secondPeek.get()) <= 0)) {
            return first.next();
        }
        return second.next();
    }

    @Override
    public Optional<Entry<M>> peek() {
        Optional<Entry<M>> firstPeek = first.peek();
        Optional<Entry<M>> secondPeek = second.peek();
        if (firstPeek.isEmpty() && !second.isCompleted()) {
            return secondPeek;
        } else if (!first.isCompleted() && firstPeek.isPresent() &&
                (secondPeek.isEmpty() || comparator.compare(firstPeek.get(), secondPeek.get()) <= 0)) {
            return firstPeek;
        }
        return secondPeek;
    }

    @Override
    public void setCallback(@NonNull Runnable callback) {
        first.setCallback(callback);
        second.setCallback(callback);
    }

    @Override
    public Optional<Throwable> error() {
        return first.error().or(second::error);
    }

    @Override
    public boolean isCompleted() {
        return first.isCompleted() && second.isCompleted();
    }

    @Override
    public boolean hasNextAvailable() {
        return first.hasNextAvailable() || second.hasNextAvailable();
    }

    @Override
    public void close() {
        first.close();
        second.close();
    }
}
