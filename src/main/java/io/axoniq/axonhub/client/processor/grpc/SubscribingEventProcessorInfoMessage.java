package io.axoniq.axonhub.client.processor.grpc;

import io.axoniq.platform.grpc.EventProcessorInfo;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import org.axonframework.eventhandling.SubscribingEventProcessor;

/**
 * Created by Sara Pellegrini on 15/03/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscribingEventProcessorInfoMessage implements PlatformInboundMessage {

    private final SubscribingEventProcessor processor;

    public SubscribingEventProcessorInfoMessage(SubscribingEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public PlatformInboundInstruction instruction() {
        EventProcessorInfo msg = EventProcessorInfo.newBuilder()
                                                   .setProcessorName(processor.getName())
                                                   .setMode("Subscribing")
                                                   .build();
        return PlatformInboundInstruction
                .newBuilder()
                .setEventProcessorInfo(msg)
                .build();
    }
}
