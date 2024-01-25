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

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

public class IterableMessageStream<T> implements MessageStream<T> {

    private final Iterable<T> source;

    public IterableMessageStream(Iterable<T> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<T> asCompletableFuture() {
        Iterator<T> iterator = source.iterator();
        if (iterator.hasNext()) {
            return CompletableFuture.completedFuture(iterator.next());
        } else {
            return FutureUtils.emptyCompletedFuture();
        }
    }

    @Override
    public Flux<T> asFlux() {
        return Flux.fromIterable(source);
    }
}
