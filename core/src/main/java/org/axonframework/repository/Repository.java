/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.repository;

/**
 * The repository provides an abstraction of the storage of aggregates.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this repository stores.
 * @since 0.1
 */
public interface Repository<T> {

    /**
     * Load the aggregate with the given unique <code>aggregateIdentifier</code>, expecting the version of the aggregate
     * to be equal to the given <code>expectedVersion</code>. If the <code>expectedVersion</code> is <code>null</code>,
     * no version validation is done.
     * <p/>
     * When versions do not match, implementations may either raise an exception immediately when loading an aggregate,
     * or at any other time while the aggregate is registered in the current Unit Of Work.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate to load, or <code>null</code> to indicate the
     *                            version should not be checked
     * @return The aggregate root with the given identifier.
     *
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     * @throws ConflictingModificationException
     *                                    if the <code>expectedVersion</code> did not match the aggregate's actual
     *                                    version
     * @see org.axonframework.unitofwork.UnitOfWork
     */
    T load(Object aggregateIdentifier, Long expectedVersion);

    /**
     * Load the aggregate with the given unique identifier. No version checks are done when loading an aggregate,
     * meaning that concurrent access will not be checked for.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @return The aggregate root with the given identifier.
     *
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     */
    T load(Object aggregateIdentifier);

    /**
     * Adds the given <code>aggregate</code> to the repository. The version of this aggregate must be <code>null</code>,
     * indicating that it has not been previously persisted.
     * <p/>
     * This method will not force the repository to save the aggregate immediately. Instead, it is registered with the
     * current UnitOfWork. To force storage of an aggregate, commit the current unit of work
     * (<code>CurrentUnitOfWork.commit()</code>)
     *
     * @param aggregate The aggregate to add to the repository.
     * @throws IllegalArgumentException if the given aggregate is not newly created. This means {@link
     *                                  org.axonframework.domain.AggregateRoot#getVersion()} must return
     *                                  <code>null</code>.
     */
    void add(T aggregate);
}
