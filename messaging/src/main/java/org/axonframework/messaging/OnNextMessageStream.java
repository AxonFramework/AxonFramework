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
 * {@link Message} is consumed from this {@code MessageStream}.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnNextMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final MessageStream<M> delegate;
    private final Consumer<M> onNext;

    /**
     * Construct an {@link OnNextMessageStream} that invokes the given {@code onNext} {@link Consumer} each time a
     * new {@link Message} is consumed by the given {@code delegate}.
     *
     * @param delegate The delegate {@link MessageStream} from which each consumed {@link Message} is given to the
     *                 {@code onNext} {@link Consumer}.
     * @param onNext   The {@link Consumer} to handle each consumed {@link Message} from the given {@code delegate}.
     */
    OnNextMessageStream(@NotNull MessageStream<M> delegate,
                        @NotNull Consumer<M> onNext) {
        this.delegate = delegate;
        this.onNext = onNext;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return delegate.asCompletableFuture()
                       .thenApply(message -> {
                           onNext.accept(message);
                           return message;
                       });
    }

    @Override
    public Flux<M> asFlux() {
        return delegate.asFlux()
                       .doOnNext(onNext);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, M, R> accumulator) {
        return delegate.reduce(identity, (base, message) -> {
            onNext.accept(message);
            return accumulator.apply(base, message);
        });
    }
}
