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
