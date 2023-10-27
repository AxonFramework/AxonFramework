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

package org.axonframework.eventsourcing.conflictresolution;

/**
 * Interface describing a factory for exceptions that indicate an unresolved conflict in an aggregate instance.
 *
 * @param <T> The type of exception created
 */
@FunctionalInterface
public interface ConflictExceptionSupplier<T extends Exception> {

    /**
     * Creates an instance of an exception indicating a conflict in an aggregate with given {@code aggregateIdentifier},
     * the given {@code expectedVersion} and {@code actualVersion}.
     *
     * @param aggregateIdentifier The identifier of the conflicting aggregate
     * @param expectedVersion     The expected version of the aggregate
     * @param actualVersion       The actual version of the aggregate
     * @return the exception describing the conflict
     */
    T supplyException(String aggregateIdentifier, long expectedVersion, long actualVersion);
}
