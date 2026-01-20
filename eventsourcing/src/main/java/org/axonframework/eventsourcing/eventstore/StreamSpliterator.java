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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An {@link Spliterators.AbstractSpliterator} implementation used internally to feed a {@link java.util.stream.Stream}
 * through the {@link java.util.stream.StreamSupport#stream(Spliterator, boolean)} operation.
 *
 * @param <T> The generic type serves by this {@link Spliterators.AbstractSpliterator} implementation.
 * @author Rene de Waele
 * @since 3.0.0
 */
@Internal
public class StreamSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

    private final Function<T, List<? extends T>> fetchFunction;
    private final Predicate<List<? extends T>> finalFetchPredicate;

    private Iterator<? extends T> iterator;
    private T lastItem;
    private boolean finalFetch;

    /**
     * Constructs a {@code StreamSpliterator} using the given {@code fetchFunction} and {@code finalFetchPredicate}.
     * <p>
     * Both lambdas are used in the {@link #tryAdvance(Consumer)} operation of this spliterator. The
     * {@code fetchFunction} retrieves collections of type {@code T}, which are fed to the {@code action} of
     * {@link #tryAdvance(Consumer)}.
     * <p>
     * The {@code finalFetchPredicate} signals when the {@code fetchFunction} has done its last fetch operation.
     * Whenever this {@link Predicate} returns {@code true}, {@link #tryAdvance(Consumer)} will return {@code false}, to
     * signal the stream is done.
     *
     * @param fetchFunction       Lambda used to fetch new entries of type {@code T} to iteratively provide to the
     *                            {@code action} of {@link #tryAdvance(Consumer)}.
     * @param finalFetchPredicate Lambda dictating when the {@code fetchFunction} has done its last fetch.
     */
    public StreamSpliterator(@Nonnull Function<T, List<? extends T>> fetchFunction,
                             @Nonnull Predicate<List<? extends T>> finalFetchPredicate) {
        super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT | CONCURRENT);
        this.fetchFunction = fetchFunction;
        this.finalFetchPredicate = finalFetchPredicate;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (iterator == null || !iterator.hasNext()) {
            if (finalFetch) {
                return false;
            }
            List<? extends T> items = fetchFunction.apply(lastItem);
            finalFetch = finalFetchPredicate.test(items);
            iterator = items.iterator();
        }
        if (!iterator.hasNext()) {
            return false;
        }

        action.accept(lastItem = iterator.next());
        return true;
    }
}
