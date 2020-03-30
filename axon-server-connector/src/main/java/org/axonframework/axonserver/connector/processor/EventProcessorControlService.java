/*
 * Copyright (c) 2010-2020. Axon Framework
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

import io.axoniq.axonserver.grpc.control.EventProcessorReference;
import io.axoniq.axonserver.grpc.control.EventProcessorSegmentReference;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.GrpcInstructionResultPublisher;
import org.axonframework.axonserver.connector.InstructionResultPublisher;
import org.axonframework.axonserver.connector.processor.grpc.GrpcEventProcessorMapping;
import org.axonframework.axonserver.connector.processor.grpc.PlatformInboundMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.StartHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.*;
import static java.lang.String.format;

/**
 * Service that listens to {@link PlatformOutboundInstruction}s to control {@link EventProcessor}s when for example
 * requested by Axon Server. Will delegate the calls to the {@link AxonServerConnectionManager} and/or {@link
 * EventProcessorController} for further processing.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class EventProcessorControlService {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorControlService.class);

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final EventProcessorController eventProcessorController;
    private final String context;
    private final Function<EventProcessor, PlatformInboundMessage> platformInboundMessageMapper;
    private final InstructionResultPublisher resultPublisher;

    /**
     * Initialize a {@link EventProcessorControlService} which adds {@link java.util.function.Consumer}s to the given
     * {@link AxonServerConnectionManager} on {@link PlatformOutboundInstruction}s. These Consumers typically leverage
     * the {@link EventProcessorController} to issue operations to the {@link EventProcessor}s contained in this
     * application. Uses the {@link AxonServerConnectionManager#getDefaultContext()} specified in the given
     * {@code axonServerConnectionManager} as the context to dispatch operations in
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} used to add operations when
     *                                    {@link PlatformOutboundInstruction} have been received
     * @param eventProcessorController    the {@link EventProcessorController} used to perform operations on the
     *                                    {@link EventProcessor}s
     * @param axonServerConfiguration     the {@link AxonServerConfiguration} used to retrieve the client identifier
     */
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessorController eventProcessorController,
                                        AxonServerConfiguration axonServerConfiguration) {
        this(axonServerConnectionManager,
             eventProcessorController,
             new GrpcInstructionResultPublisher(axonServerConnectionManager, axonServerConfiguration),
             axonServerConfiguration.getContext());
    }

    /**
     * Initialize a {@link EventProcessorControlService} which adds {@link java.util.function.Consumer}s to the given
     * {@link AxonServerConnectionManager} on {@link PlatformOutboundInstruction}s. These Consumers typically leverage
     * the {@link EventProcessorController} to issue operations to the {@link EventProcessor}s contained in this
     * application. Uses the {@link AxonServerConnectionManager#getDefaultContext()} specified in the given
     * {@code axonServerConnectionManager} as the context to dispatch operations in
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} used to add operations when
     *                                    {@link PlatformOutboundInstruction} have been received
     * @param eventProcessorController    the {@link EventProcessorController} used to perform operations on the
     *                                    {@link EventProcessor}s
     * @param context                     the context of this application instance within which outbound instruction
     *                                    handlers should be specified on the given {@code axonServerConnectionManager}
     */
    public EventProcessorControlService(AxonServerConnectionManager axonServerConnectionManager,
                                        EventProcessorController eventProcessorController,
                                        InstructionResultPublisher instructionResultPublisher,
                                        String context) {
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.eventProcessorController = eventProcessorController;
        this.resultPublisher = instructionResultPublisher;
        this.context = context;
        this.platformInboundMessageMapper = new GrpcEventProcessorMapping();
    }

    /**
     * Add {@link java.util.function.Consumer}s to the {@link AxonServerConnectionManager} for several {@link
     * PlatformOutboundInstruction}s. Will be started in phase {@link Phase#INBOUND_EVENT_CONNECTORS}, to ensure the
     * event processors this service provides control over have been started.
     */
    @SuppressWarnings("Duplicates")
    @StartHandler(phase = Phase.INSTRUCTION_COMPONENTS)
    public void start() {
        this.axonServerConnectionManager.onOutboundInstruction(context, PAUSE_EVENT_PROCESSOR, this::pauseProcessor);
        this.axonServerConnectionManager.onOutboundInstruction(context, START_EVENT_PROCESSOR, this::startProcessor);
        this.axonServerConnectionManager.onOutboundInstruction(context, RELEASE_SEGMENT, this::releaseSegment);
        this.axonServerConnectionManager.onOutboundInstruction(
                context, REQUEST_EVENT_PROCESSOR_INFO, this::getEventProcessorInfo
        );
        this.axonServerConnectionManager.onOutboundInstruction(
                context, SPLIT_EVENT_PROCESSOR_SEGMENT, this::splitSegment
        );
        this.axonServerConnectionManager.onOutboundInstruction(
                context, MERGE_EVENT_PROCESSOR_SEGMENT, this::mergeSegment
        );
    }

    private void pauseProcessor(PlatformOutboundInstruction platformOutboundInstruction) {
        EventProcessorReference pauseEventProcessor = platformOutboundInstruction.getPauseEventProcessor();
        String processorName = pauseEventProcessor.getProcessorName();
        eventProcessorController.pauseProcessor(processorName);
    }

    private void startProcessor(PlatformOutboundInstruction platformOutboundInstruction) {
        EventProcessorReference startEventProcessor = platformOutboundInstruction.getStartEventProcessor();
        String processorName = startEventProcessor.getProcessorName();
        eventProcessorController.startProcessor(processorName);
    }

    private void releaseSegment(PlatformOutboundInstruction platformOutboundInstruction) {
        EventProcessorSegmentReference releaseSegment = platformOutboundInstruction.getReleaseSegment();
        String processorName = releaseSegment.getProcessorName();
        int segmentIdentifier = releaseSegment.getSegmentIdentifier();
        eventProcessorController.releaseSegment(processorName, segmentIdentifier);
    }

    private void getEventProcessorInfo(PlatformOutboundInstruction platformOutboundInstruction) {
        EventProcessorReference requestInfo = platformOutboundInstruction.getRequestEventProcessorInfo();
        String processorName = requestInfo.getProcessorName();
        try {
            EventProcessor processor = eventProcessorController.getEventProcessor(processorName);
            axonServerConnectionManager.send(context, platformInboundMessageMapper.apply(processor).instruction());
        } catch (Exception e) {
            logger.debug("Problem getting the information about Event Processor [{}]", processorName, e);
        }
    }

    private void splitSegment(PlatformOutboundInstruction platformOutboundInstruction) {
        EventProcessorSegmentReference splitSegment = platformOutboundInstruction.getSplitEventProcessorSegment();
        int segmentId = splitSegment.getSegmentIdentifier();
        String processorName = splitSegment.getProcessorName();
        String errorMessage = format("Failed to split segment [%s] for processor [%s]", segmentId, processorName);
        try {
            if (!eventProcessorController.splitSegment(processorName, segmentId)) {
                throw new RuntimeException(errorMessage);
            }
            resultPublisher.publishSuccessFor(platformOutboundInstruction.getInstructionId());
        } catch (Exception e) {
            logger.error(errorMessage, e);
            resultPublisher.publishFailureFor(platformOutboundInstruction.getInstructionId(), e);
        }
    }

    private void mergeSegment(PlatformOutboundInstruction platformOutboundInstruction) {
        EventProcessorSegmentReference mergeSegment = platformOutboundInstruction.getMergeEventProcessorSegment();
        String processorName = mergeSegment.getProcessorName();
        int segmentId = mergeSegment.getSegmentIdentifier();
        String errorMessage = format("Failed to merge segment [%s] for processor [%s]", segmentId, processorName);
        try {
            if (!eventProcessorController.mergeSegment(processorName, segmentId)) {
                throw new RuntimeException(errorMessage);
            }
            resultPublisher.publishSuccessFor(platformOutboundInstruction.getInstructionId());
        } catch (Exception e) {
            logger.error(errorMessage, e);
            resultPublisher.publishFailureFor(platformOutboundInstruction.getInstructionId(), e);
        }
    }
}
