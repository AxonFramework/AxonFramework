/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.unitofwork;

/**
 * Abstract implementation of the {@link UnitOfWorkListener} that exposes an extra convenience method: {@link
 * #onCommitOrRollback()}. This implementation does nothing by itself, other than delegating {@link #afterCommit()} and
 * {@link #onRollback()} to {@link #onCommitOrRollback()}.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class UnitOfWorkListenerAdapter implements UnitOfWorkListener {

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation does nothing.
     */
    @Override
    public void onPrepareCommit() {
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation only calls the {@link #onCommitOrRollback()} method.
     */
    @Override
    public void afterCommit() {
        onCommitOrRollback();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation only calls the {@link #onCommitOrRollback()} method.
     */
    @Override
    public void onRollback() {
        onCommitOrRollback();
    }

    /**
     * Called when the UnitOfWork this listener was registered to either committed or was rolled back.
     */
    public void onCommitOrRollback() {
    }
}
