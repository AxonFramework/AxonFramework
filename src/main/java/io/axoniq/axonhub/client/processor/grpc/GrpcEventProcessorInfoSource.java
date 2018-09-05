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

package io.axoniq.axonhub.client.processor.grpc;

import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.processor.AxonHubEventProcessorInfoSource;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by Sara Pellegrini on 15/03/2018.
 * sara.pellegrini@gmail.com
 */
public class GrpcEventProcessorInfoSource implements AxonHubEventProcessorInfoSource {

    private final Map<String, PlatformInboundInstruction> lastProcessorsInfo = new HashMap<>();

    private final EventProcessors eventProcessors;

    private final Consumer<PlatformInboundInstruction> send;

    private final Function<EventProcessor, PlatformInboundMessage> mapping;

    public GrpcEventProcessorInfoSource(EventHandlingConfiguration eventHandlingConfiguration,
                                        PlatformConnectionManager platformConnectionManager) {
        this(new EventProcessors(eventHandlingConfiguration),
             platformConnectionManager::send,
             new GrpcEventProcessorMapping());
        platformConnectionManager.addReconnectListener(lastProcessorsInfo::clear);
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
