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

import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

class StreamMessageStream<T extends Message<?>> implements MessageStream<T> {

    private final Stream<T> source;

    public StreamMessageStream(Stream<T> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<T> asCompletableFuture() {
        return CompletableFuture.completedFuture(source.findFirst().orElse(null));
    }

    @Override
    public Flux<T> asFlux() {
        return Flux.fromStream(source);
    }

    @Override
    public <R extends Message<?>> MessageStream<R> map(Function<T, R> mapper) {
        return new StreamMessageStream<>(source.map(mapper));
    }
}
