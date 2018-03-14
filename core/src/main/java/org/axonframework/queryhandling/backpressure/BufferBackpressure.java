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
 *
 *
 */

package org.axonframework.queryhandling.backpressure;

import org.axonframework.queryhandling.UpdateHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffer backpressure mechanism - updates will be stored in internal buffer, and when {@code bufferLimit} is reached
 * {@code original} {@link UpdateHandler} will be invoked with list of previously collected updates. {@code original}
 * {@link UpdateHandler} may choose which updates to process. Updates are sorted in arrival order.
 *
 * @param <I> type of initial result
 * @param <U> type of incremental update. Do note that incremental update type of {@code original} {@link UpdateHandler}
 *            is {@code List<U>}
 * @author Milan Savic
 * @since 3.3
 */
public class BufferBackpressure<I, U> implements Backpressure<I, U> {

    private final UpdateHandler<I, List<U>> original;
    private final List<U> buffer;
    private final int bufferLimit;

    /**
     * Initializes buffer backpressure mechanism with {@code original} {@link UpdateHandler} and {@code bufferLimit}.
     * When {@code bufferLimit} is reached {@code original} {@link UpdateHandler} will be invoked with accumulated list
     * of updates.
     *
     * @param original    original update handler
     * @param bufferLimit buffer limit
     */
    public BufferBackpressure(UpdateHandler<I, List<U>> original, int bufferLimit) {
        this.original = original;
        this.bufferLimit = bufferLimit;
        this.buffer = new ArrayList<>(bufferLimit);
    }

    @Override
    public void onInitialResult(I initial) {
        original.onInitialResult(initial);
    }

    @Override
    public void onUpdate(U update) {
        synchronized (buffer) {
            buffer.add(update);

            if (buffer.size() == bufferLimit) {
                original.onUpdate(new ArrayList<>(buffer));
                buffer.clear();
            }
        }
    }

    @Override
    public void onCompleted() {
        original.onCompleted();
    }

    @Override
    public void onError(Throwable error) {
        original.onError(error);
    }
}
