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
import org.axonframework.common.FutureUtils;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link MessageStream stream} implementation that contains no entries at all.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class EmptyMessageStream<E> implements MessageStream<E> {

    @SuppressWarnings("rawtypes")
    private static final EmptyMessageStream INSTANCE = new EmptyMessageStream<>();

    private EmptyMessageStream() {
        // No-arg constructor to enforce use of INSTANCE constant.
    }

    /**
     * Return a singular instance of the {@link EmptyMessageStream} to be used throughout.
     *
     * @param <E> The type of entry carried in this {@link MessageStream stream}.
     * @return A singular instance of the {@link EmptyMessageStream} to be used throughout.
     */
    public static <E> EmptyMessageStream<E> instance() {
        //noinspection unchecked
        return INSTANCE;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public Flux<E> asFlux() {
        return Flux.empty();
    }

    @Override
    public <R> MessageStream<R> map(@NotNull Function<E, R> mapper) {
        //noinspection unchecked
        return (MessageStream<R>) this;
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return CompletableFuture.completedFuture(identity);
    }

    @Override
    public MessageStream<E> onNextItem(Consumer<E> onNext) {
        return this;
    }

    @Override
    public MessageStream<E> onErrorContinue(Function<Throwable, MessageStream<E>> onError) {
        return this;
    }

    @Override
    public MessageStream<E> whenComplete(Runnable completeHandler) {
        try {
            completeHandler.run();
            return this;
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
