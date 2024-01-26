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

public interface MessageStream<T> {

    static <T> MessageStream<T> fromIterable(Iterable<T> instance) {
        return new IterableMessageStream<>(instance);
    }

    static <T> MessageStream<T> fromFuture(CompletableFuture<T> instance) {
        return new SingleValueMessageStream<>(instance);
    }

    static <T> MessageStream<T> fromStream(Stream<T> stream) {
        return new StreamMessageStream<>(stream);
    }

    static <T> MessageStream<T> just(T instance) {
        return new SingleValueMessageStream<>(instance);
    }

    static <T> MessageStream<T> failed(Throwable error) {
        return new FailedMessageStream<>(error);
    }

    static MessageStream<Void> empty() {
        return EmptyMessageStream.instance();
    }

    CompletableFuture<T> asCompletableFuture();

    Flux<T> asFlux();

    default <R> MessageStream<R> map(Function<T, R> mapper) {
        return new MappedMessageStream<>(this, mapper);
    }

    default MessageStream<T> whenComplete(Runnable completeHandler) {
        return new CompletionCallbackMessageStream<>(this, completeHandler);
    }

    default MessageStream<T> onErrorContinue(Function<Throwable, MessageStream<T>> onError) {
        return new OnErrorContinueMessageStream<>(this, onError);
    }
}
