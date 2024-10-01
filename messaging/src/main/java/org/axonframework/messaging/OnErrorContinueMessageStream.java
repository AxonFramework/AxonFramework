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

import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Implementation of the {@link MessageStream} that when the stream completes exceptionally will continue on a
 * {@code MessageStream} returned by the given {@code onError} {@link Function}.
 *
 * @param <E> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnErrorContinueMessageStream<E> implements MessageStream<E> {

    private final MessageStream<E> delegate;
    private final Function<Throwable, MessageStream<E>> onError;

    /**
     * Construct an {@link OnErrorContinueMessageStream} that will proceed on the resulting {@link MessageStream} from
     * the given {@code onError} when the {@code delegate} completes exceptionally
     *
     * @param delegate The delegate {@link MessageStream} to proceed from with the result of {@code onError} <em>if</em>
     *                 it completes exceptionally.
     * @param onError  A {@link Function} providing the replacement {@link MessageStream} to continue from if the given
     *                 {@code delegate} completes exceptionally.
     */
    OnErrorContinueMessageStream(@NotNull MessageStream<E> delegate,
                                 @NotNull Function<Throwable, MessageStream<E>> onError) {
        this.delegate = delegate;
        this.onError = onError;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return delegate.asCompletableFuture()
                       .exceptionallyCompose(exception -> onError.apply(exception)
                                                                 .asCompletableFuture());
    }

    @Override
    public Flux<E> asFlux() {
        return delegate.asFlux()
                       .onErrorResume(exception -> onError.apply(exception)
                                                          .asFlux());
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, E, R> accumulator) {
        StatefulAccumulator<R> wrapped = new StatefulAccumulator<>(identity, accumulator);
        return delegate.reduce(identity, wrapped)
                       .exceptionallyCompose(exception -> onError.apply(exception)
                                                                 .reduce(wrapped.latest(), wrapped));
    }

    private class StatefulAccumulator<R> implements BiFunction<R, E, R> {

        private final AtomicReference<R> latest;
        private final BiFunction<R, E, R> accumulator;

        public StatefulAccumulator(R identity, BiFunction<R, E, R> accumulator) {
            this.latest = new AtomicReference<>(identity);
            this.accumulator = accumulator;
        }

        @Override
        public R apply(R initial, E message) {
            R result = accumulator.apply(initial, message);
            latest.set(result);
            return result;
        }

        R latest() {
            return latest.get();
        }
    }
}
