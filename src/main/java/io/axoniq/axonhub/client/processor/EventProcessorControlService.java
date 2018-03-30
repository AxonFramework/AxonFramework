package io.axoniq.axonhub.client.processor;

import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.platform.grpc.PauseEventProcessor;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import io.axoniq.platform.grpc.StartEventProcessor;

import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.PAUSE_EVENT_PROCESSOR;
import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.START_EVENT_PROCESSOR;



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
    }

    public void init(){
        this.platformConnectionManager.onOutboundInstruction(PAUSE_EVENT_PROCESSOR, this::pauseProcessor);
        this.platformConnectionManager.onOutboundInstruction(START_EVENT_PROCESSOR, this::startProcessor);
    }

    public void pauseProcessor(PlatformOutboundInstruction platformOutboundInstruction) {
        PauseEventProcessor pauseEventProcessor = platformOutboundInstruction.getPauseEventProcessor();
        String processorName = pauseEventProcessor.getProcessorName();
        eventProcessorController.pauseProcessor(processorName);
    }

    public void startProcessor(PlatformOutboundInstruction platformOutboundInstruction) {
        StartEventProcessor startEventProcessor = platformOutboundInstruction.getStartEventProcessor();
        String processorName = startEventProcessor.getProcessorName();
        eventProcessorController.startProcessor(processorName);
    }





}
