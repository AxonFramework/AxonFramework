/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * Wrapper around {@link Flux}. Since project-reactor is not required dependency in this Axon version, we need wrappers
 * for backwards compatibility. As soon as dependency is no longer optional, this wrapper should be removed.
 *
 * @param <T> The value type
 * @author Milan Savic
 * @since 3.3
 */
class FluxWrapper<T> {

    private final Flux<T> flux;

    /**
     * Initializes this wrapper with delegate flux.
     *
     * @param flux Delegate flux
     */
    FluxWrapper(Flux<T> flux) {
        this.flux = flux;
    }

    /**
     * Gets the delegate flux.
     *
     * @return delegate flux
     */
    public Flux<T> getFlux() {
        return flux;
    }

    /**
     * Creates the wrapper by creating the delegate flux.
     *
     * @param callback     Gets wrapper around {@link reactor.core.publisher.FluxSink} which can be used to emit
     *                     updates, close the stream, etc.
     * @param backpressure Wrapper around {@link reactor.core.publisher.FluxSink.OverflowStrategy}
     * @param <T>          The type of the data sent via this flux
     * @return instance of created wrapper
     */
    public static <T> FluxWrapper<T> create(Consumer<? super FluxSinkWrapper<T>> callback,
                                     SubscriptionQueryBackpressure backpressure) {
        return new FluxWrapper<>(Flux.create(emitter -> callback.accept(new FluxSinkWrapper<>(emitter)),
                                             backpressure.getOverflowStrategy()));
    }
}
