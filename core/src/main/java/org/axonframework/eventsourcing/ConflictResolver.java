/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.commandhandling.model.ConflictingModificationException;

import java.util.List;

/**
 * Interface describing an object that is capable of detecting conflicts between changes applied to an aggregate, and
 * unseen changes made to the aggregate. If any such conflicts are detected, an instance of {@link
 * ConflictingModificationException} (or subtype) is thrown.
 *
 * @author Allard Buijze
 * @since 0.6
 */
@Deprecated // TODO Remove in Axon 3.
public interface ConflictResolver {

    /**
     * Checks the given list of <code>appliedChanges</code> and <code>committedChanges</code> for any conflicting
     * changes. If any such conflicts are detected, an instance of
     * {@link ConflictingModificationException} (or subtype) is thrown. If no conflicts
     * are detected, nothing happens.
     *
     * @param appliedChanges   The list of the changes applied to the aggregate
     * @param committedChanges The list of events that have been previously applied, but were unexpected by the command
     *                         handler
     * @throws ConflictingModificationException
     *          if any conflicting changes are detected
     */
    void resolveConflicts(List<DomainEventMessage<?>> appliedChanges, List<DomainEventMessage<?>> committedChanges);
}
