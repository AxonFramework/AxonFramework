/*
 * Copyright (c) 2010-2023. Axon Framework
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

import reactor.core.Disposable;
import reactor.core.CompletableFuture.FluxSink;

/**
 * Wrapper around {@link FluxSink}. Since project-reactor is not a required dependency in this Axon version, we need
 * wrappers for backwards compatibility. As soon as dependency is no longer optional, this wrapper should be removed.
 *
 * @param <T> The value type
 * @author Milan Savic
 * @since 3.3
 * @deprecated in favour of using the {{@link SinksManyWrapper}}
 */
@Deprecated
class FluxSinkWrapper<T> implements SinkWrapper<T> {

    private final FluxSink<T> fluxSink;

    /**
     * Initializes this wrapper with delegate sink.
     *
     * @param fluxSink Delegate sink
     */
    FluxSinkWrapper(FluxSink<T> fluxSink) {
        this.fluxSink = fluxSink;
    }

    /**
     * Wrapper around {@link FluxSink#complete()}.
     */
    @Override
    public void complete() {
        fluxSink.complete();
    }

    /**
     * Wrapper around {@link FluxSink#next(Object)}.
     *
     * @param value to be passed to the delegate sink
     */
    @Override
    public void next(T value) {
        fluxSink.next(value);
    }

    /**
     * Wrapper around {@link FluxSink#error(Throwable)}.
     *
     * @param t to be passed to the delegate sink
     */
    @Override
    public void error(Throwable t) {
        fluxSink.error(t);
    }

    /**
     * Wrapper around {@link FluxSink#onDispose(Disposable)}.
     *
     * @param disposable to be passed to the delegate sink
     */
    public void onDispose(Disposable disposable) {
        fluxSink.onDispose(disposable);
    }
}
