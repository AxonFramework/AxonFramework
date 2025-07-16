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

package org.axonframework.eventhandling.pipeline;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SegmentMatcher;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;

// TODO: Decide, alternative for SegmentMatchingEventHandlingComponent
public class SegmentMatchingEventProcessingPipeline implements EventProcessingPipeline {

    private final EventProcessingPipeline next;
    private final SegmentMatcher segmentMatcher;

    public SegmentMatchingEventProcessingPipeline(EventProcessingPipeline next, SegmentMatcher segmentMatcher) {
        this.next = next;
        this.segmentMatcher = segmentMatcher;
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events, ProcessingContext context, Segment segment) {
        var matchingEvents = events.stream()
                .filter(event -> segmentMatcher.matches(segment, event))
                .toList();
        return next.process(matchingEvents, context, segment);
    }
}
