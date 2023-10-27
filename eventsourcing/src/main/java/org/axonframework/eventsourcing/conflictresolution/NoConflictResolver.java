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

import org.axonframework.eventhandling.DomainEventMessage;

import java.util.List;
import java.util.function.Predicate;

/**
 * Implementation of a {@link ConflictResolver} that does nothing. This implementation can be used in cases where
 * no actual conflict is present.
 *
 * @author Rene de Waele
 */
public enum NoConflictResolver implements ConflictResolver {

    /**`
     * Singleton {@link NoConflictResolver} instance
     */
    INSTANCE;


    @Override
    public <T extends Exception> void detectConflicts(Predicate<List<DomainEventMessage<?>>> predicate,
                                                      ConflictExceptionSupplier<T> exceptionSupplier) {
        // no op
    }

    @Override
    public void detectConflicts(Predicate<List<DomainEventMessage<?>>> predicate) {
        // no op
    }

    @Override
    public <T extends Exception> void detectConflicts(Predicate<List<DomainEventMessage<?>>> predicate, ContextAwareConflictExceptionSupplier<T> exceptionSupplier) {
        // no op
    }


}
