package io.axoniq.axonhub.client.event;

import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.event.axon.EventProcessorController;
import io.axoniq.platform.grpc.EventProcessorPaused;
import io.axoniq.platform.grpc.EventProcessorStarted;
import io.axoniq.platform.grpc.PauseEventProcessor;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import io.axoniq.platform.grpc.StartEventProcessor;

import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.PAUSE_EVENT_PROCESSOR;
import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.STAR_EVENT_PROCESSOR;

/**
 * Created by Sara Pellegrini on 09/03/2018.
 * sara.pellegrini@gmail.com
 */
public class EventProcessorControlService {

    private final PlatformConnectionManager platformConnectionManager;

    private final EventProcessorController eventProcessorController;

    public EventProcessorControlService(PlatformConnectionManager platformConnectionManager,
                                        EventProcessorController eventProcessorController) {
        this.platformConnectionManager = platformConnectionManager;
        this.eventProcessorController = eventProcessorController;

        this.platformConnectionManager.onOutboundInstruction(PAUSE_EVENT_PROCESSOR, this::pauseProcessor);
        this.platformConnectionManager.onOutboundInstruction(STAR_EVENT_PROCESSOR, this::startProcessor);

        this.eventProcessorController.onPause(this::notifyProcessorPaused);
        this.eventProcessorController.onStart(this::notifyProcessorStarted);
    }

    public void pauseProcessor(PlatformOutboundInstruction platformOutboundInstruction){
        PauseEventProcessor pauseEventProcessor = platformOutboundInstruction.getPauseEventProcessor();
        String processorName = pauseEventProcessor.getProcessorName();
        eventProcessorController.pauseProcessor(processorName);
    }

    public void startProcessor(PlatformOutboundInstruction platformOutboundInstruction){
        StartEventProcessor starEventProcessor = platformOutboundInstruction.getStarEventProcessor();
        String processorName = starEventProcessor.getProcessorName();
        eventProcessorController.startProcessor(processorName);
    }

    public void notifyProcessorStarted(String processorName){
        EventProcessorStarted.Builder message = EventProcessorStarted.newBuilder().setProcessorName(processorName);
        PlatformInboundInstruction instruction = PlatformInboundInstruction.newBuilder()
                                                                           .setEventProcessorStarted(message)
                                                                           .build();
        platformConnectionManager.send(instruction);
    }

    public void notifyProcessorPaused(String processorName){
        EventProcessorPaused.Builder message = EventProcessorPaused.newBuilder().setProcessorName(processorName);
        PlatformInboundInstruction instruction = PlatformInboundInstruction.newBuilder()
                                                                           .setEventProcessorPaused(message)
                                                                           .build();
        platformConnectionManager.send(instruction);
    }


}
