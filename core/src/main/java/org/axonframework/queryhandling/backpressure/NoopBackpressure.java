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

package org.axonframework.queryhandling.backpressure;

import org.axonframework.queryhandling.UpdateHandler;

/**
 * Noop implementation of {@link BackpressuredUpdateHandler}. Just delegates to original {@link UpdateHandler}.
 *
 * @param <I> type of initial result
 * @param <U> type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public class NoopBackpressure<I, U> implements BackpressuredUpdateHandler<I, U> {

    private final UpdateHandler<I, U> delegate;

    /**
     * Initializes Noop backpressure with delegate update handler.
     *
     * @param delegate delegate update handler
     */
    public NoopBackpressure(UpdateHandler<I, U> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onInitialResult(I initial) {
        delegate.onInitialResult(initial);
    }

    @Override
    public void onUpdate(U update) {
        delegate.onUpdate(update);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }

    @Override
    public void onCompletedExceptionally(Throwable error) {
        delegate.onCompletedExceptionally(error);
    }
}
