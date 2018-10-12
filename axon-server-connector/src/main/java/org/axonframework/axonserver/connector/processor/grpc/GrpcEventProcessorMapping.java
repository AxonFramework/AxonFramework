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

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;

import java.util.function.Function;

/**
 * Mapping that translates an {@link EventProcessor} to GRPC {@link PlatformInboundMessage} representing the status of the {@link EventProcessor}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcEventProcessorMapping implements Function<EventProcessor, PlatformInboundMessage> {

    @Override
    public PlatformInboundMessage apply(EventProcessor processor) {
        if (processor instanceof TrackingEventProcessor)
            return new TrackingEventProcessorInfoMessage((TrackingEventProcessor) processor);
        if (processor instanceof SubscribingEventProcessor)
            return new SubscribingEventProcessorInfoMessage((SubscribingEventProcessor) processor);
        throw new RuntimeException("Unknown instance of Event Processor detected.");
    }
}
