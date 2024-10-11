/*
 * Copyright (c) 2010-2024. Axon Framework
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
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implementation of the {@link MessageStream} that when the stream completes exceptionally will continue on a
 * {@code MessageStream} returned by the given {@code onError} {@link Function}.
 *
 * @param <M> The type of {@link Message} contained in the {@link MessageEntry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnErrorContinueMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final MessageStream<M> delegate;
    private final Function<Throwable, MessageStream<M>> onError;

    /**
     * Construct an {@link MessageStream stream} that will proceed on the resulting {@code MessageStream} from the given
     * {@code onError} when the {@code delegate} completes exceptionally
     *
     * @param delegate The delegate {@link MessageStream stream} to proceed from with the result of {@code onError}
     *                 <em>if</em> it completes exceptionally.
     * @param onError  A {@link Function} providing the replacement {@link MessageStream stream} to continue from if the
     *                 given {@code delegate} completes exceptionally.
     */
    OnErrorContinueMessageStream(@Nonnull MessageStream<M> delegate,
                                 @Nonnull Function<Throwable, MessageStream<M>> onError) {
        this.delegate = delegate;
        this.onError = onError;
    }

    @Override
    public CompletableFuture<MessageEntry<M>> asCompletableFuture() {
        return delegate.asCompletableFuture()
                       .exceptionallyCompose(exception -> onError.apply(exception)
                                                                 .asCompletableFuture());
    }

    @Override
    public Flux<MessageEntry<M>> asFlux() {
        return delegate.asFlux()
                       .onErrorResume(exception -> onError.apply(exception)
                                                          .asFlux());
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, MessageEntry<M>, R> accumulator) {
        StatefulAccumulator<R> wrapped = new StatefulAccumulator<>(identity, accumulator);
        return delegate.reduce(identity, wrapped)
                       .exceptionallyCompose(exception -> onError.apply(exception)
                                                                 .reduce(wrapped.latest(), wrapped));
    }

    private class StatefulAccumulator<R> implements BiFunction<R, MessageEntry<M>, R> {

        private final AtomicReference<R> latest;
        private final BiFunction<R, MessageEntry<M>, R> accumulator;

        public StatefulAccumulator(R identity,
                                   BiFunction<R, MessageEntry<M>, R> accumulator) {
            this.latest = new AtomicReference<>(identity);
            this.accumulator = accumulator;
        }

        @Override
        public R apply(R initial, MessageEntry<M> entry) {
            R result = accumulator.apply(initial, entry);
            latest.set(result);
            return result;
        }

        R latest() {
            return latest.get();
        }
    }
}
