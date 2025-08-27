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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;

import java.util.Iterator;
import java.util.Optional;

/**
 * A {@link MessageStream} implementation using an {@link Iterator} as the source for {@link Entry entries}.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class IteratorMessageStream<M extends Message> implements MessageStream<M> {

    private final Iterator<? extends Entry<M>> source;
    private Entry<M> peeked = null;

    /**
     * Constructs a {@link MessageStream stream} using the given {@code source} to provide the {@link Entry entries}.
     *
     * @param source The {@link Iterator} providing the {@link Entry entries} for this {@link MessageStream stream}.
     */
    IteratorMessageStream(@Nonnull Iterator<? extends Entry<M>> source) {
        this.source = source;
    }

    @Override
    public Optional<Entry<M>> next() {
        if (peeked != null) {
            Entry<M> result = peeked;
            peeked = null;
            return Optional.of(result);
        }
        if (source.hasNext()) {
            return Optional.of(source.next());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Entry<M>> peek() {
        if (peeked != null) {
            return Optional.of(peeked);
        }
        if (source.hasNext()) {
            peeked = source.next();
            return Optional.of(peeked);
        }
        return Optional.empty();
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        callback.run();
    }

    @Override
    public Optional<Throwable> error() {
        return Optional.empty();
    }

    @Override
    public boolean isCompleted() {
        return !source.hasNext();
    }

    @Override
    public boolean hasNextAvailable() {
        return source.hasNext();
    }

    @Override
    public void close() {
    }

}
