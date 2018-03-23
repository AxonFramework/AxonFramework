package io.axoniq.axonhub.client.processor.grpc;

import io.axoniq.platform.grpc.EventProcessorInfo;
import io.axoniq.platform.grpc.EventProcessorInfo.EventTrackerInfo;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Created by Sara Pellegrini on 15/03/2018.
 * sara.pellegrini@gmail.com
 */
public class TrackingEventProcessorInfoMessage implements PlatformInboundMessage {

    private final TrackingEventProcessor processor;

    public TrackingEventProcessorInfoMessage(TrackingEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public PlatformInboundInstruction instruction() {
        Map<Integer, EventTrackerStatus> statusMap = processor.processingStatus();


        List<EventTrackerInfo> trackers = statusMap
                .entrySet()
                .stream()
                .map(e -> EventTrackerInfo.newBuilder()
                                          .setSegmentId(e.getKey())
                                          .setCaughtUp(e.getValue().isCaughtUp())
                                          .setReplaying(e.getValue().isReplaying())
                                          .setOnePartOf(e.getValue().getSegment().getMask()+1)

                     .build())
                .collect(toList());

        EventProcessorInfo msg = EventProcessorInfo.newBuilder()
                                                   .setProcessorName(processor.getName())
                                                   .setMode("Tracking")
                                                   .setActiveThreads(processor.activeProcessorThreads())
                                                   .setRunning(processor.isRunning())
                                                   .setError(processor.isError())
                                                   .addAllEventTrackersInfo(trackers)
                                                   .build();
        return PlatformInboundInstruction
                .newBuilder()
                .setEventProcessorInfo(msg)
                .build();
    }
}
