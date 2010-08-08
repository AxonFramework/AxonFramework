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

package org.axonframework.repository;

import org.axonframework.domain.AggregateRoot;

import java.util.UUID;

/**
 * The repository provides an abstraction of the storage of aggregates.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this repository stores.
 * @since 0.1
 */
public interface Repository<T extends AggregateRoot> {

    /**
     * Gets the aggregate with the given unique identifier. If the actual version of the aggregate does not match the
     * given <code>expectedVersion</code>, the repository may throw a {@link ConflictingModificationException} if that
     * is considered a conflict. This exception may be thrown at any time during the UnitOfWork lifecycle.
     *
     * @param aggregateIdentifier The identifier of the aggregate to get
     * @param expectedVersion     The expected version of the aggregate to get
     * @return The aggregate root with the given identifier.
     *
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     * @throws ConflictingModificationException
     *                                    if the expected version does not match the actual version in the repository.
     */
    T get(UUID aggregateIdentifier, Long expectedVersion);

    /**
     * Adds the given <code>aggregate</code> to the repository. The version of this aggregate must be <code>null</code>,
     * indicating that it has not been previously persisted.
     * <p/>
     * This method will not force the repository to save the aggregate. Instead, it is registered with the current
     * UnitOfWork. To force the aggregate from being saved, use either {@link #save(AggregateRoot)}, or commit the
     * UnitOfWork.
     *
     * @param aggregate The aggregate to add to the repository.
     */
    void add(T aggregate);

    /**
     * Store the given aggregate. If an aggregate with the same unique identifier already exists, it is updated
     * instead.
     * <p/>
     * New aggregates will automatically be added to the repository, if that has not been done explicitly using {@link
     * #add(org.axonframework.domain.AggregateRoot)}
     * <p/>
     * Note: It is recommended to use a UnitOfWork instead. This method will be deprecated soon in favor of a call to
     * <code>CurrentUnitOfWork.commit()</code>, or better, a UnitOfWork interceptor on the command bus.
     *
     * @param aggregate The aggregate root of the aggregate to store.
     * @see org.axonframework.commandhandling.interceptors.SimpleUnitOfWorkInterceptor
     */
    void save(T aggregate);

    /**
     * Load the aggregate with the given unique identifier.
     *
     * @param aggregateIdentifier The identifier of the aggregate to get
     * @return The aggregate root with the given identifier.
     *
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     * @deprecated This method does not allow you to check for concurrent modification. Use the {@link
     *             #get(java.util.UUID, Long)} method instead.
     */
    @Deprecated
    T load(UUID aggregateIdentifier);
}
