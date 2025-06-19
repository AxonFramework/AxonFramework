/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Extension of {@link EventHandlingComponent} for components that need to be aware
 * of segment lifecycle events and handle segmented event processing.
 *
 * @author Mateusz Nowak
 * @since 5.0
 */
public interface SegmentAwareEventHandlingComponent extends EventHandlingComponent {

    /**
     * This is a way for an event processor to communicate that a segment which was being processed is released. This
     * might be needed or required to free resources, or clean up state which is related to the {@link Segment}.
     *
     * @param segment the segment which was released.
     */
    void segmentReleased(Segment segment);

    /**
     * Check whether or not this component can handle the given {@code eventMessage} for a given segment.
     * This method helps determine if an event should be processed by a particular segment.
     *
     * @param eventMessage The message to be processed.
     * @param context      The {@code ProcessingContext} in which the event handler will be invoked.
     * @param segment      The segment for which the event handler should be invoked.
     * @return {@code true} if the component can handle the given message for the specified segment, {@code false} otherwise.
     */
    boolean canHandleInSegment(EventMessage<?> eventMessage, ProcessingContext context, Segment segment);

    /**
     * Returns the sequence identifier for the given event message. This is used to determine which segment
     * an event should be processed in. Events with the same sequence identifier are guaranteed to be processed
     * by the same segment.
     *
     * @param eventMessage The message to get the sequence identifier for
     * @return The sequence identifier for this message
     */
    default Object getSequenceIdentifier(EventMessage<?> eventMessage) {
        return eventMessage.getIdentifier();
    }
}
