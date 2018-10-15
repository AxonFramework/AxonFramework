/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

package org.axonframework.axonserver.connector.processor.grpc;

import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorInfoSource;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of {@link EventProcessorInfoSource} that send {@link EventProcessor}'s status to AxonServer using GRPC messages.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcEventProcessorInfoSource implements EventProcessorInfoSource {

    private final Map<String, PlatformInboundInstruction> lastProcessorsInfo = new HashMap<>();

    private final EventProcessors eventProcessors;

    private final Consumer<PlatformInboundInstruction> send;

    private final Function<EventProcessor, PlatformInboundMessage> mapping;

    public GrpcEventProcessorInfoSource(EventProcessingConfiguration eventProcessingConfiguration,
                                        AxonServerConnectionManager axonServerConnectionManager) {
        this(new EventProcessors(eventProcessingConfiguration),
             axonServerConnectionManager::send,
             new GrpcEventProcessorMapping());
        axonServerConnectionManager.addReconnectListener(lastProcessorsInfo::clear);
    }

    GrpcEventProcessorInfoSource(EventProcessors eventProcessors,
                                        Consumer<PlatformInboundInstruction> send,
                                        Function<EventProcessor, PlatformInboundMessage> mapping) {
        this.eventProcessors = eventProcessors;
        this.send = send;
        this.mapping = mapping;
    }

    @Override
    public void notifyInformation() {
        eventProcessors.forEach(processor -> {
            PlatformInboundInstruction instruction = mapping.apply(processor).instruction();
            if (!instruction.equals(lastProcessorsInfo.get(processor.getName()))){
                send.accept(instruction);
            }
            lastProcessorsInfo.put(processor.getName(), instruction);
        });
    }

}
