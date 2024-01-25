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

import org.axonframework.common.FutureUtils;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

class EmptyMessageStream implements MessageStream<Void> {

    public static final EmptyMessageStream INSTANCE = new EmptyMessageStream();

    public static EmptyMessageStream instance() {
        return INSTANCE;
    }

    private EmptyMessageStream() {
    }

    @Override
    public CompletableFuture<Void> asCompletableFuture() {
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public Flux<Void> asFlux() {
        return Flux.empty();
    }

    @Override
    public <R> MessageStream<R> map(Function<Void, R> mapper) {
        //noinspection unchecked
        return (MessageStream<R>) this;
    }

    @Override
    public MessageStream<Void> whenComplete(Runnable completeHandler) {
        try {
            completeHandler.run();
            return this;
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    @Override
    public MessageStream<Void> onErrorContinue(Function<Throwable, MessageStream<Void>> onError) {
        return this;
    }
}
