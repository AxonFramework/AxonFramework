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

import java.util.Optional;
import java.util.function.Consumer;

/**
 * An implementation of the {@link MessageStream} that invokes the given {@code onNext} {@link Consumer} each time a new
 * {@link Entry entry} is consumed from this {@code MessageStream}.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnNextMessageStream<M extends Message<?>> extends DelegatingMessageStream<M, M> {

    private final MessageStream<M> delegate;
    private final Consumer<Entry<M>> onNext;

    /**
     * Construct an {@link MessageStream stream} that invokes the given {@code onNext} {@link Consumer} each time a new
     * {@link Entry entry} is consumed by the given {@code delegate}.
     *
     * @param delegate The delegate {@link MessageStream stream} from which each consumed {@link Entry entry} is given
     *                 to the {@code onNext} {@link Consumer}.
     * @param onNext   The {@link Consumer} to handle each consumed {@link Entry entry} from the given
     *                 {@code delegate}.
     */
    OnNextMessageStream(@Nonnull MessageStream<M> delegate, @Nonnull Consumer<Entry<M>> onNext) {
        super(delegate);
        this.delegate = delegate;
        this.onNext = onNext;
    }

    @Override
    public Optional<Entry<M>> next() {
        Optional<Entry<M>> next = delegate.next();
        next.ifPresent(onNext);
        return next;
    }

    static class Single<M extends Message<?>> extends OnNextMessageStream<M> implements MessageStream.Single<M> {

        /**
         * Construct an {@link MessageStream stream} that invokes the given {@code onNext} {@link Consumer} each time a
         * new {@link Entry entry} is consumed by the given {@code delegate}.
         *
         * @param delegate The delegate {@link MessageStream stream} from which each consumed {@link Entry entry} is
         *                 given to the {@code onNext} {@link Consumer}.
         * @param onNext   The {@link Consumer} to handle each consumed {@link Entry entry} from the given
         *                 {@code delegate}.
         */
        Single(@Nonnull MessageStream.Single<M> delegate, @Nonnull Consumer<Entry<M>> onNext) {
            super(delegate, onNext);
        }
    }
}
