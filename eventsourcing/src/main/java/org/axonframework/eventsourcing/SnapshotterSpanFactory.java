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

import org.axonframework.tracing.Span;

/**
 * Span factory that creates spans for the {@link Snapshotter}.
 * You can customize the spans of the snapshotter by creating your own implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface SnapshotterSpanFactory {
    /**
     * Creates a new {@link Span} that represents the scheduling of snapshot creation to the snapshotter's executor.
     *
     * @param aggregateType       The aggregate's type.
     * @param aggregateIdentifier The aggregate's identifier.
     * @return A {@link Span} representing the scheduling of snapshot creation.
     */
    Span createScheduleSnapshotSpan(String aggregateType, String aggregateIdentifier);

    /**
     * Creates a new {@link Span} that represents the actual creation of a snapshot. The creation
     * of a snapshot might be done in a separate thread depending on the implementation of the  {@link Snapshotter}.
     *
     * @param aggregateType       The aggregate's type.
     * @param aggregateIdentifier The aggregate's identifier.
     * @return A {@link Span} representing the creation of a snapshot.
     */
    Span createCreateSnapshotSpan(String aggregateType, String aggregateIdentifier);
}
