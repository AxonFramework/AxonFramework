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

package org.axonframework.core;

/**
 * Interface for aggregate roots that provide their version number. This version number is used to implement locking
 * strategies and efficient detection of changes.
 *
 * @author Allard Buijze
 * @see org.axonframework.core.repository.LockingRepository
 * @since 0.3
 */
public interface VersionedAggregateRoot extends AggregateRoot {

    /**
     * Returns the sequence number of the last committed event on this aggregate or <code>null</code> if events were
     * ever committed. This sequence number can be used to implement optimistic locking strategies.
     *
     * @return the sequence number of the last committed event or <code>null</code> if no events were ever committed
     */
    Long getLastCommittedEventSequenceNumber();
}
