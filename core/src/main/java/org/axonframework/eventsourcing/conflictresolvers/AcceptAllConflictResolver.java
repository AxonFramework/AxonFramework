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

package org.axonframework.eventsourcing.conflictresolvers;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventsourcing.ConflictResolver;

import java.util.List;

/**
 * Implementation of the conflict resolver that will accept all changes made to an aggregate,  even if the aggregate
 * contains changes that were not expected by the command handler.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class AcceptAllConflictResolver implements ConflictResolver {

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation does nothing, hence accepting all unseen changes
     */
    @Override
    public void resolveConflicts(List<DomainEventMessage> appliedChanges, List<DomainEventMessage> committedChanges) {
    }
}
