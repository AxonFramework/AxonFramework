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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation that contains no {@link Message Messages} at all.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class EmptyMessageStream<M extends Message<?>> implements MessageStream<M> {

    @SuppressWarnings("rawtypes")
    private static final EmptyMessageStream INSTANCE = new EmptyMessageStream<>();

    private EmptyMessageStream() {
    }

    /**
     * Return a singular instance of the {@link EmptyMessageStream} to be used throughout.
     *
     * @param <M> The type of {@link Message} carried in this stream.
     * @return A singular instance of the {@link EmptyMessageStream} to be used throughout.
     */
    public static <M extends Message<?>> EmptyMessageStream<M> instance() {
        //noinspection unchecked
        return INSTANCE;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public Flux<M> asFlux() {
        return Flux.empty();
    }

    @Override
    public <R extends Message<?>> MessageStream<R> map(@NotNull Function<M, R> mapper) {
        //noinspection unchecked
        return (MessageStream<R>) this;
    }

    @Override
    public MessageStream<M> onNextItem(Consumer<M> onNext) {
        return this;
    }

    @Override
    public MessageStream<M> onErrorContinue(Function<Throwable, MessageStream<M>> onError) {
        return this;
    }

    @Override
    public MessageStream<M> whenComplete(Runnable completeHandler) {
        try {
            completeHandler.run();
            return this;
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
