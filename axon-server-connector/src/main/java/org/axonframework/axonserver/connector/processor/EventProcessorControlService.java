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

package org.axonframework.axonserver.connector.processor;

import org.axonframework.axonserver.connector.PlatformConnectionManager;
import org.axonframework.axonserver.connector.processor.grpc.GrpcEventProcessorMapping;
import org.axonframework.axonserver.connector.processor.grpc.PlatformInboundMessage;
import io.axoniq.axonserver.grpc.control.PauseEventProcessor;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import io.axoniq.axonserver.grpc.control.ReleaseEventProcessorSegment;
import io.axoniq.axonserver.grpc.control.RequestEventProcessorInfo;
import io.axoniq.axonserver.grpc.control.StartEventProcessor;
import org.axonframework.eventhandling.EventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.*;


/**
 * Created by Sara Pellegrini on 09/03/2018.
 * sara.pellegrini@gmail.com
 */
public class EventProcessorControlService {

    private final Logger logger = LoggerFactory.getLogger(EventProcessorControlService.class);

    private final PlatformConnectionManager platformConnectionManager;

    private final EventProcessorController eventProcessorController;

    private final Function<EventProcessor, PlatformInboundMessage> mapping;

    public EventProcessorControlService(PlatformConnectionManager platformConnectionManager,
                                        EventProcessorController eventProcessorController) {
        this.platformConnectionManager = platformConnectionManager;
        this.eventProcessorController = eventProcessorController;
        this.mapping = new GrpcEventProcessorMapping();
    }

    public void start(){
        this.platformConnectionManager.onOutboundInstruction(PAUSE_EVENT_PROCESSOR, this::pauseProcessor);
        this.platformConnectionManager.onOutboundInstruction(START_EVENT_PROCESSOR, this::startProcessor);
        this.platformConnectionManager.onOutboundInstruction(RELEASE_SEGMENT, this::releaseSegment);
        this.platformConnectionManager.onOutboundInstruction(REQUEST_EVENT_PROCESSOR_INFO, this::getEventProcessorInfo);
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

    public void getEventProcessorInfo(PlatformOutboundInstruction platformOutboundInstruction){
        try {
            RequestEventProcessorInfo request = platformOutboundInstruction.getRequestEventProcessorInfo();
            EventProcessor processor = eventProcessorController.getEventProcessor(request.getProcessorName());
            platformConnectionManager.send(mapping.apply(processor).instruction());
        } catch (Exception e) {
            logger.debug("Problem getting the information about Event Processor",e);
        }
    }

}
