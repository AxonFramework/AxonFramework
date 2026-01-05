/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.segmenting;

import jakarta.annotation.Nonnull;

/**
 * Interface for components that maintain per-{@link Segment} state and need to be notified when a segment is released.
 * <p>
 * Components implementing this interface can use the {@link #segmentReleased(Segment)} callback to clean up any
 * resources or cached data associated with a specific segment. This is particularly useful for:
 * <ul>
 *   <li>Clearing per-segment caches to prevent memory leaks</li>
 *   <li>Releasing per-segment resources when a segment is no longer being processed</li>
 *   <li>Performing cleanup operations when a segment is split or merged</li>
 * </ul>
 * <p>
 * In the context of event processors, this callback is typically invoked when:
 * <ul>
 *   <li>A work package for a segment is aborted or completed</li>
 *   <li>The processor releases ownership of a segment</li>
 *   <li>The processor is shut down</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 * @see Segment
 */
public interface SegmentAware {

    /**
     * Called when a {@link Segment} is released, allowing the component to clean up any resources or cached data
     * associated with that segment.
     * <p>
     * Implementations should use this callback to clear per-segment state, such as caches or buffers, to prevent
     * memory leaks and ensure proper resource management.
     *
     * @param segment The {@link Segment} that was released.
     */
    void segmentReleased(@Nonnull Segment segment);
}
