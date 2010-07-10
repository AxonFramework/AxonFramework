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
 * Interface describing a listener that is notified of state changes in the UnitOfWork it has been registered with.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface UnitOfWorkListener {

    /**
     * Invoked when the UnitOfWork is committed. When processing of this method causes an exception, a UnitOfWork may
     * choose to call {@link #onRollback()} consecutively.
     *
     * @see UnitOfWork#commit()
     */
    void afterCommit();

    /**
     * Invoked when the UnitOfWork is rolled back. The UnitOfWork may choose to invoke this method when committing the
     * UnitOfWork failed, too.
     *
     * @see UnitOfWork#rollback()
     */
    void onRollback();
}
