/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * Wrapper around {@link Mono}. Since project-reactor is not a required dependency in this Axon version, we need
 * wrappers for backwards compatibility. As soon as dependency is no longer optional, this wrapper should be removed.
 *
 * @param <T> The value type
 * @author Milan Savic
 * @since 3.3
 */
class MonoWrapper<T> {

    private final Mono<T> mono;

    /**
     * Initializes this wrapper with delegate mono.
     *
     * @param mono Delegate mono
     */
    MonoWrapper(Mono<T> mono) {
        this.mono = mono;
    }

    /**
     * Gets mono delegate.
     *
     * @return mono delegate
     */
    public Mono<T> getMono() {
        return mono;
    }

    /**
     * Creates the wrapper by creating the delegate mono.
     *
     * @param callback Gets the wrapper around {@link reactor.core.publisher.MonoSink} which can be used to complete the
     *                 mono
     * @param <T>      The type of data sent via this mono
     * @return instance of created wrapper
     */
    public static <T> MonoWrapper<T> create(Consumer<MonoSinkWrapper<T>> callback) {
        return new MonoWrapper<>(Mono.create(monoSink -> callback.accept(new MonoSinkWrapper<>(monoSink))));
    }
}
