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

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.function.Supplier;

/**
 * An {@link EventHandlingComponent} that delegates the handling of events to another component, but only if the current
 * segment matches the segment of the event.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class SegmentMatchingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final SegmentMatcher segmentMatcher;
    private final Supplier<Segment> segmentSupplier;

    /**
     * Constructs the DelegatingEventHandlingComponent with given {@code delegate} to receive calls.
     *
     * @param delegate        The instance to delegate calls to.
     * @param segmentMatcher  The {@link SegmentMatcher} to determine if the current segment should handle the event.
     * @param segmentSupplier Supplies the {@link Segment} to check the event against.
     */
    public SegmentMatchingEventHandlingComponent(@Nonnull EventHandlingComponent delegate,
                                                 @Nonnull SegmentMatcher segmentMatcher,
                                                 @Nonnull Supplier<Segment> segmentSupplier) {
        super(delegate);
        this.segmentMatcher = segmentMatcher;
        this.segmentSupplier = segmentSupplier;
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        var segment = segmentSupplier.get();
        return segmentMatcher.matches(segment, event)
                ? delegate.handle(event, context)
                : MessageStream.empty();
    }
}
