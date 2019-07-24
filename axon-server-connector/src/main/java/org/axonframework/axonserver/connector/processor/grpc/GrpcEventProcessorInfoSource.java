/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.processor.grpc;

import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorInfoSource;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of {@link EventProcessorInfoSource} that send {@link EventProcessor}'s status to Axon Server using
 * gRPC messages.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcEventProcessorInfoSource implements EventProcessorInfoSource {

    private static final Logger logger = LoggerFactory.getLogger(GrpcEventProcessorInfoSource.class);

    private final EventProcessors eventProcessors;
    private final Consumer<PlatformInboundInstruction> platformInstructionSender;
    private final Function<EventProcessor, PlatformInboundMessage> platformInboundMessageMapper;
    private final Map<String, PlatformInboundInstruction> lastProcessorsInfo;

    private final AtomicBoolean logError = new AtomicBoolean(true);

    /**
     * Instantiate a {@link EventProcessorInfoSource} which can send {@link EventProcessor} status info to Axon Server.
     * It uses the given {@link EventProcessingConfiguration} to retrieve all the EventProcessor instances and the
     * provided {@link AxonServerConnectionManager} to send message to Axon Server.
     *
     * @param eventProcessingConfiguration the {@link EventProcessingConfiguration} from which the existing
     *                                     {@link EventProcessor} instances are retrieved
     * @param axonServerConnectionManager  the {@link AxonServerConnectionManager} used to send message to Axon Server
     * @param context                      the context of this application instance in which {@link EventProcessor}
     *                                     status' should be send
     */
    public GrpcEventProcessorInfoSource(EventProcessingConfiguration eventProcessingConfiguration,
                                        AxonServerConnectionManager axonServerConnectionManager,
                                        String context) {
        this(
                new EventProcessors(eventProcessingConfiguration),
                instruction -> axonServerConnectionManager.send(context, instruction),
                new GrpcEventProcessorMapping()
        );
        axonServerConnectionManager.addReconnectListener(context, lastProcessorsInfo::clear);
    }

    private GrpcEventProcessorInfoSource(EventProcessors eventProcessors,
                                         Consumer<PlatformInboundInstruction> platformInstructionSender,
                                         Function<EventProcessor, PlatformInboundMessage> platformInboundMessageMapper) {
        this.eventProcessors = eventProcessors;
        this.platformInstructionSender = platformInstructionSender;
        this.platformInboundMessageMapper = platformInboundMessageMapper;
        this.lastProcessorsInfo = new HashMap<>();
    }

    @Override
    public void notifyInformation() {
        try {
            eventProcessors.forEach(processor -> {
                PlatformInboundInstruction instruction = platformInboundMessageMapper.apply(processor).instruction();
                if (!instruction.equals(lastProcessorsInfo.get(processor.getName()))) {
                    platformInstructionSender.accept(instruction);
                }
                lastProcessorsInfo.put(processor.getName(), instruction);
            });
            logError.set(true);
        } catch (Exception | OutOfDirectMemoryError e) {
            if (logError.get()) {
                logger.warn("Sending processor status failed: {}", e.getMessage());
                logError.set(false);
            }
        }
    }
}
