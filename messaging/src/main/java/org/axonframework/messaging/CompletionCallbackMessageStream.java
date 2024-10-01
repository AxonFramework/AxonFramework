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

/**
 * Implementation of the {@link MessageStream} that invokes the given {@code completeHandler} once the
 * {@code delegate MessageStream} completes.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class CompletionCallbackMessageStream<E> implements MessageStream<E> {

    private final MessageStream<E> delegate;
    private final Runnable completeHandler;

    /**
     * Construct a {@link MessageStream stream} invoking the given {@code completeHandler} when the given
     * {@code delegate} completes.
     *
     * @param delegate        The {@link MessageStream stream} that once completed results in the invocation of the
     *                        given {@code completeHandler}.
     * @param completeHandler The {@link Runnable} to invoke when the given {@code delegate} completes.
     */
    CompletionCallbackMessageStream(@NotNull MessageStream<E> delegate,
                                    @NotNull Runnable completeHandler) {
        this.delegate = delegate;
        this.completeHandler = completeHandler;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return delegate.asCompletableFuture()
                       .whenComplete((result, exception) -> {
                           if (exception == null) {
                               completeHandler.run();
                           }
                       });
    }

    @Override
    public Flux<E> asFlux() {
        return delegate.asFlux()
                       .doOnComplete(completeHandler);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return delegate.reduce(identity, accumulator)
                       .whenComplete((result, exception) -> {
                           if (exception == null) {
                               completeHandler.run();
                           }
                       });
    }
}
