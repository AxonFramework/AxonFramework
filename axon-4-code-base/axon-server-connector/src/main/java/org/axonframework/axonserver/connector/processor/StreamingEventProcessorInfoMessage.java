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

package org.axonframework.axonserver.connector.processor;

import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo.SegmentStatus;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Supplier of {@link PlatformInboundInstruction} that represent the status of a {@link StreamingEventProcessor}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class StreamingEventProcessorInfoMessage {

    private StreamingEventProcessorInfoMessage() {
    }

    /**
     * Create an {@link EventProcessorInfo} based on the given {@code eventProcessor}.
     *
     * @param eventProcessor the {@link StreamingEventProcessor} to base an {@link EventProcessorInfo} on
     * @return a {@link EventProcessorInfo} based on the given {@code eventProcessor}
     */
    public static EventProcessorInfo describe(StreamingEventProcessor eventProcessor) {
        List<SegmentStatus> segmentStatuses = eventProcessor.processingStatus()
                                                            .values()
                                                            .stream()
                                                            .map(StreamingEventProcessorInfoMessage::buildSegmentStatus)
                                                            .collect(toList());

        return EventProcessorInfo.newBuilder()
                                 .setProcessorName(eventProcessor.getName())
                                 .setTokenStoreIdentifier(eventProcessor.getTokenStoreIdentifier())
                                 .setMode(defineMode(eventProcessor.getClass()))
                                 .setActiveThreads(eventProcessor.processingStatus().size())
                                 .setAvailableThreads(eventProcessor.maxCapacity()-eventProcessor.processingStatus().size())
                                 .setRunning(eventProcessor.isRunning())
                                 .setError(eventProcessor.isError())
                                 .addAllSegmentStatus(segmentStatuses)
                                 .setIsStreamingProcessor(true)
                                 .build();
    }

    private static String defineMode(Class<? extends StreamingEventProcessor> streamingProcessorClass) {
        if (streamingProcessorClass.isAssignableFrom(TrackingEventProcessor.class)) {
            return "Tracking";
        } else if (streamingProcessorClass.isAssignableFrom(PooledStreamingEventProcessor.class)) {
            return "Pooled Streaming";
        } else {
            return "Streaming";
        }
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
}
