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
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Implementation of the {@link MessageStream} that when the stream completes exceptionally will continue on a
 * {@code MessageStream} returned by the given {@code onError} {@link Function}.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnErrorContinueMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final MessageStream<M> delegate;
    private final Function<Throwable, MessageStream<M>> onError;

    /**
     * Construct an {@link OnErrorContinueMessageStream} that will proceed on the resulting {@link MessageStream} from
     * the given {@code onError} when the {@code delegate} completes exceptionally
     *
     * @param delegate The delegate {@link MessageStream} to proceed from with the result of {@code onError} <em>if</em>
     *                 it completes exceptionally.
     * @param onError  A {@link Function} providing the replacement {@link MessageStream} to continue from if the given
     *                 {@code delegate} completes exceptionally.
     */
    OnErrorContinueMessageStream(@NotNull MessageStream<M> delegate,
                                 @NotNull Function<Throwable, MessageStream<M>> onError) {
        this.delegate = delegate;
        this.onError = onError;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return delegate.asCompletableFuture().exceptionallyCompose(e -> onError.apply(e).asCompletableFuture());
    }

    @Override
    public Flux<M> asFlux() {
        return delegate.asFlux().onErrorResume(e -> onError.apply(e).asFlux());
    }
}
