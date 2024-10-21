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

package org.axonframework.queryhandling;

import reactor.core.publisher.Sinks;

import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * Wrapper around {@link Sinks.Many}. Since project-reactor is not a required dependency in this Axon version, we need
 * wrappers for backwards compatibility. As soon as dependency is no longer optional, this wrapper should be removed.
 *
 * @param <T> The value type
 * @author Stefan Dragisic
 * @since 4.5
 */
class SinksManyWrapper<T> implements SinkWrapper<T> {

    private final Sinks.Many<T> fluxSink;

    /**
     * Initializes this wrapper with delegate sink.
     *
     * @param fluxSink Delegate sink
     */
    SinksManyWrapper(Sinks.Many<T> fluxSink) {
        this.fluxSink = fluxSink;
    }

    /**
     * Wrapper around {@link Sinks.Many#tryEmitComplete()}. Throws exception on failure cases.}.
     */
    @Override
    public void complete() {
        performWithBusyWaitSpin(fluxSink::tryEmitComplete).orThrow();
    }

    /**
     * Wrapper around {@link Sinks.Many#tryEmitNext(Object)}. Throws exception on failure cases.
     *
     * @param value to be passed to the delegate sink
     */
    @Override
    public void next(T value) {
        performWithBusyWaitSpin(() -> fluxSink.tryEmitNext(value)).orThrow();
    }

    /**
     * Wrapper around {@link Sinks.Many#tryEmitError(Throwable)}. Throws exception on failure cases.
     *
     * @param t to be passed to the delegate sink
     */
    @Override
    public void error(Throwable t) {
        performWithBusyWaitSpin(() -> fluxSink.tryEmitError(t)).orThrow();
    }

    private Sinks.EmitResult performWithBusyWaitSpin(Supplier<Sinks.EmitResult> action) {
        int i = 0;
        Sinks.EmitResult result;
        while ((result = action.get()) == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            // For 100 iterations, just busy-spin. Will resolve most conditions.
            if (i < 100) {
                i++;
                // Busy spin...
            } else if (i < 200) {
                // For the next 100 iterations, yield, to force other threads to have a chance.
                i++;
                Thread.yield();
            } else {
                // Then after, park the thread to force other threads to perform their work.
                LockSupport.parkNanos(100);
            }
        }
        return result;
    }
}
