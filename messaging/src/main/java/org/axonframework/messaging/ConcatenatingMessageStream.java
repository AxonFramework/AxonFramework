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
 * Implementation of the {@link MessageStream} that concatenates two {@code MessageStreams}.
 * <p>
 * Will only start streaming entries of type {@code E} from the {@code second MessageStream} when the
 * {@code first MessageStream} completes successfully.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class ConcatenatingMessageStream<E> implements MessageStream<E> {

    private final MessageStream<E> first;
    private final MessageStream<E> second;

    /**
     * Construct a {@link MessageStream stream} that initially consume from the {@code first MessageStream}, followed by
     * the {@code second} if the {@code first MessageStream} completes successfully
     *
     * @param first  The initial {@link MessageStream stream} to consume entries from.
     * @param second The second {@link MessageStream stream} to start consuming from once the {@code first} stream
     *               completes successfully.
     */
    ConcatenatingMessageStream(@NotNull MessageStream<E> first,
                               @NotNull MessageStream<E> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return first.asCompletableFuture()
                    .thenCompose(message -> message == null
                            ? second.asCompletableFuture()
                            : CompletableFuture.completedFuture(message)
                    );
    }

    @Override
    public Flux<E> asFlux() {
        return first.asFlux()
                    .concatWith(second.asFlux());
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return first.reduce(identity, accumulator)
                    .thenCompose(intermediate -> second.reduce(intermediate, accumulator));
    }
}
