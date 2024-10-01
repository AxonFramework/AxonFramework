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
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * An implementation of the {@link MessageStream} that invokes the given {@code onNext} {@link Consumer} each time a new
 * entry of type {@code E} is consumed from this {@code MessageStream}.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnNextMessageStream<E> implements MessageStream<E> {

    private final MessageStream<E> delegate;
    private final Consumer<E> onNext;

    /**
     * Construct an {@link MessageStream stream} that invokes the given {@code onNext} {@link Consumer} each time a new
     * entry of type {@code E} is consumed by the given {@code delegate}.
     *
     * @param delegate The delegate {@link MessageStream stream} from which each consumed entry of type {@code E} is
     *                 given to the {@code onNext} {@link Consumer}.
     * @param onNext   The {@link Consumer} to handle each consumed entry of type {@code E} from the given
     *                 {@code delegate}.
     */
    OnNextMessageStream(@NotNull MessageStream<E> delegate,
                        @NotNull Consumer<E> onNext) {
        this.delegate = delegate;
        this.onNext = onNext;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return delegate.asCompletableFuture()
                       .thenApply(message -> {
                           onNext.accept(message);
                           return message;
                       });
    }

    @Override
    public Flux<E> asFlux() {
        return delegate.asFlux()
                       .doOnNext(onNext);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return delegate.reduce(identity, (base, message) -> {
            onNext.accept(message);
            return accumulator.apply(base, message);
        });
    }
}
