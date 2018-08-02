/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.axonhub.client.processor;

import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.platform.grpc.PauseEventProcessor;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import io.axoniq.platform.grpc.ReleaseEventProcessorSegment;
import io.axoniq.platform.grpc.StartEventProcessor;

import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.PAUSE_EVENT_PROCESSOR;
import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.RELEASE_SEGMENT;
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

    public void start(){
        this.platformConnectionManager.onOutboundInstruction(PAUSE_EVENT_PROCESSOR, this::pauseProcessor);
        this.platformConnectionManager.onOutboundInstruction(START_EVENT_PROCESSOR, this::startProcessor);
        this.platformConnectionManager.onOutboundInstruction(RELEASE_SEGMENT, this::releaseSegment);
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

    public void releaseSegment(PlatformOutboundInstruction platformOutboundInstruction) {
        ReleaseEventProcessorSegment releaseSegment = platformOutboundInstruction.getReleaseSegment();
        String processorName = releaseSegment.getProcessorName();
        int segmentIdentifier = releaseSegment.getSegmentIdentifier();
        eventProcessorController.releaseSegment(processorName, segmentIdentifier);
    }

}
