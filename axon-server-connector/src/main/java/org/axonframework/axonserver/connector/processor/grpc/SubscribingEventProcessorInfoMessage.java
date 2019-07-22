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

import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.eventhandling.SubscribingEventProcessor;

/**
 * Supplier of {@link PlatformInboundInstruction} that represent the status of a {@link SubscribingEventProcessor}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class SubscribingEventProcessorInfoMessage implements PlatformInboundMessage {

    private static final String EVENT_PROCESSOR_MODE = "Subscribing";

    private final SubscribingEventProcessor eventProcessor;

    /**
     * Instantiate a {@link PlatformInboundInstruction} representing the status of the given
     * {@link SubscribingEventProcessor}
     *
     * @param eventProcessor a {@link SubscribingEventProcessor} for which the status will be mapped to a
     *                       {@link PlatformInboundInstruction}
     */
    SubscribingEventProcessorInfoMessage(SubscribingEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @Override
    public PlatformInboundInstruction instruction() {
        EventProcessorInfo eventProcessorInfo = EventProcessorInfo.newBuilder()
                                                                  .setProcessorName(eventProcessor.getName())
                                                                  .setMode(EVENT_PROCESSOR_MODE)
                                                                  .build();
        return PlatformInboundInstruction.newBuilder()
                                         .setEventProcessorInfo(eventProcessorInfo)
                                         .build();
    }
}
