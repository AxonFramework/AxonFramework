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
import org.axonframework.common.FutureUtils;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream stream} implementation that contains no {@link Entry entries} at all.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class EmptyMessageStream implements MessageStream.Empty<Message<Void>> {

    private static final EmptyMessageStream INSTANCE = new EmptyMessageStream();

    private EmptyMessageStream() {
        // Private no-arg constructor to enforce use of INSTANCE constant.
    }

    /**
     * Return a singular instance of the {@code EmptyMessageStream} to be used throughout.
     *
     * @return The singular instance of the {@code EmptyMessageStream} to be used throughout.
     */
    public static Empty<Message<Void>> instance() {
        return INSTANCE;
    }

    @Override
    public CompletableFuture<Entry<Message<Void>>> asCompletableFuture() {
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public Flux<Entry<Message<Void>>> asFlux() {
        return Flux.empty();
    }

    @Override
    public Optional<Entry<Message<Void>>> next() {
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
        return true;
    }

    @Override
    public boolean hasNextAvailable() {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity, @Nonnull BiFunction<R, Entry<Message<Void>>, R> accumulator) {
        return CompletableFuture.completedFuture(identity);
    }

    @Override
    public MessageStream<Message<Void>> onErrorContinue(@Nonnull Function<Throwable, MessageStream<Message<Void>>> onError) {
        return this;
    }

    @Override
    public Empty<Message<Void>> whenComplete(@Nonnull Runnable completeHandler) {
        try {
            completeHandler.run();
            return this;
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
