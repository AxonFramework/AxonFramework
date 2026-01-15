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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo.SegmentStatus;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.EventTrackerStatus;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessor;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Utility class constructing {@link EventProcessorInfo} instances for all known {@link EventProcessor} types.
 *
 * @author Sara Pellegrini
 * @since 4.0.0
 */
@Internal
final class EventProcessorInfoUtils {

    private static final String POOLED_STREAMING = "Pooled Streaming";
    private static final String SUBSCRIBING = "Subscribing";
    private static final String UNKNOWN = "Unknown";

    /**
     * Create an {@link EventProcessorInfo} based on the given {@code streamingProcessor}.
     *
     * @param streamingProcessor The {@link StreamingEventProcessor} to base an {@link EventProcessorInfo} on.
     * @return An {@link EventProcessorInfo} based on the given {@code streamingProcessor}.
     */
    @Nonnull
    public static EventProcessorInfo describeStreaming(@Nonnull StreamingEventProcessor streamingProcessor) {
        List<SegmentStatus> segmentStatuses = streamingProcessor.processingStatus()
                                                                .values()
                                                                .stream()
                                                                .map(EventProcessorInfoUtils::buildSegmentStatus)
                                                                .collect(toList());

        return EventProcessorInfo.newBuilder()
                                 .setProcessorName(streamingProcessor.name())
                                 .setTokenStoreIdentifier(streamingProcessor.getTokenStoreIdentifier())
                                 .setMode(POOLED_STREAMING)
                                 .setActiveThreads(streamingProcessor.processingStatus().size())
                                 .setAvailableThreads(
                                         streamingProcessor.maxCapacity() - streamingProcessor.processingStatus().size()
                                 )
                                 .setRunning(streamingProcessor.isRunning())
                                 .setError(streamingProcessor.isError())
                                 .addAllSegmentStatus(segmentStatuses)
                                 .setIsStreamingProcessor(true)
                                 .build();
    }

    private static SegmentStatus buildSegmentStatus(EventTrackerStatus status) {
        return SegmentStatus.newBuilder()
                            .setSegmentId(status.getSegment().getSegmentId())
                            .setCaughtUp(status.isCaughtUp())
                            .setReplaying(status.isReplaying())
                            .setOnePartOf(status.getSegment().getMask() + 1)
                            .setTokenPosition(getPosition(status.getTrackingToken()))
                            .setErrorState(status.isErrorState() ? buildErrorMessage(status.getError()) : "")
                            .build();
    }

    private static long getPosition(TrackingToken trackingToken) {
        long position = 0;
        if (trackingToken != null) {
            position = trackingToken.position().orElse(0);
        }
        return position;
    }

    private static String buildErrorMessage(Throwable error) {
        return error.getClass().getName() + ": " + error.getMessage();
    }

    /**
     * Create an {@link EventProcessorInfo} based on the given {@code subscribingProcessor}.
     *
     * @param subscribingProcessor The {@link SubscribingEventProcessor} to base an {@link EventProcessorInfo} on.
     * @return An {@link EventProcessorInfo} based on the given {@code subscribingProcessor}.
     */
    @Nonnull
    public static EventProcessorInfo describeSubscribing(@Nonnull SubscribingEventProcessor subscribingProcessor) {
        return EventProcessorInfo.newBuilder()
                                 .setProcessorName(subscribingProcessor.name())
                                 .setMode(SUBSCRIBING)
                                 .setIsStreamingProcessor(false)
                                 .build();
    }

    /**
     * Create an {@link EventProcessorInfo} based on the given {@code unknownProcessor} type.
     *
     * @param unknownProcessor The unknown {@link EventProcessor} type to base an {@link EventProcessorInfo} on.
     * @return An {@link EventProcessorInfo} based on the given {@code unknownProcessor}.
     */
    @Nonnull
    public static EventProcessorInfo describeUnknown(@Nonnull EventProcessor unknownProcessor) {
        return EventProcessorInfo.newBuilder()
                                 .setProcessorName(unknownProcessor.name())
                                 .setMode(UNKNOWN)
                                 .setIsStreamingProcessor(false)
                                 .build();
    }

    private EventProcessorInfoUtils() {
        // Utility class
    }
}
