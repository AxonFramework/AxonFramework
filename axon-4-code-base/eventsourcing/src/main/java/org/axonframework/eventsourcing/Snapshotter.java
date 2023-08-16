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

package org.axonframework.eventsourcing;

import javax.annotation.Nonnull;

/**
 * Interface describing instances that are capable of creating snapshot events for aggregates. Although snapshotting is
 * typically an asynchronous process, implementations may to choose to create snapshots in the calling thread.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface Snapshotter {

    /**
     * Schedules snapshot taking for an aggregate with given {@code aggregateIdentifier}. The implementation may choose
     * to process this call synchronously (i.e. in the caller's thread), asynchronously, or ignore the call altogether.
     *
     * @param aggregateType       the type of the aggregate to take the snapshot for
     * @param aggregateIdentifier The identifier of the aggregate to take the snapshot for
     */
    void scheduleSnapshot(@Nonnull Class<?> aggregateType, @Nonnull String aggregateIdentifier);
}
