/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.conflictresolution;

import org.axonframework.commandhandling.model.ConflictingAggregateVersionException;
import org.axonframework.commandhandling.model.ConflictingModificationException;
import org.axonframework.eventsourcing.DomainEventMessage;

import java.util.List;
import java.util.function.Predicate;

/**
 * Interface describing an object that is capable of detecting conflicts between changes to be applied to an aggregate,
 * and unseen changes made to the aggregate. If any such conflicts are detected, an instance of {@link
 * ConflictingModificationException} (or subtype) is thrown.
 *
 * @author Rene de Waele
 */
public interface ConflictResolver {

    /**
     * Resolve conflicts between unseen changes made to the aggregate and new changes that are about to be made.
     * <p>
     * Conflicts are detected using the given {@code predicate}. If the {@link Predicate#test(Object)} method
     * returns {@code true} a conflict is registered. If a conflict is registered an instance of {@link
     * ConflictingModificationException} (or subtype) is thrown immediately. The cause of the exception is supplied
     * by the given {@code causeSupplier} (supplying a {@code null} cause is allowed).
     *
     * @param predicate     test for conflicting unseen changes. Returns {@code true} if there is a conflict.
     * @param exceptionSupplier exception to throw when a conflict is detected
     * @throws T The type of exception to throw when conflicts are detected
     */
    <T extends Exception> void detectConflicts(Predicate<List<DomainEventMessage<?>>> predicate,
                         ConflictExceptionSupplier<T> exceptionSupplier) throws T;

    /**
     * Resolve conflicts between changes to be applied to the aggregate and unseen changes made to the aggregate.
     * <p>
     * Conflicts are detected using the given {@code predicate}. If the {@link Predicate#test(Object)} method
     * returns {@code true} a conflict is registered. If a conflict is registered an instance of {@link
     * ConflictingModificationException} (or subtype) without cause is thrown immediately.
     *
     * @param predicate test for conflicting unseen changes. Returns {@code true} if there is a conflict.
     */
    default void detectConflicts(Predicate<List<DomainEventMessage<?>>> predicate) {
        detectConflicts(predicate, ConflictingAggregateVersionException::new);
    }

}
