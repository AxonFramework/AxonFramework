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
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;

import java.util.List;

/**
 * Created by Sara Pellegrini on 15/03/2018.
 * sara.pellegrini@gmail.com
 */
public class GrpcEventProcessorInfoSource implements AxonHubEventProcessorInfoSource {

    private final EventHandlingConfiguration eventHandlingConfiguration;

    private final PlatformConnectionManager platformConnectionManager;

    public GrpcEventProcessorInfoSource(EventHandlingConfiguration eventHandlingConfiguration,
                                        PlatformConnectionManager platformConnectionManager) {
        this.eventHandlingConfiguration = eventHandlingConfiguration;
        this.platformConnectionManager = platformConnectionManager;
    }

    @Override
    public void notifyInformation() {
        List<EventProcessor> processors = eventHandlingConfiguration.getProcessors();
        processors.forEach(processor -> {
            PlatformInboundMessage message = messageFor(processor);
            platformConnectionManager.send(message.instruction());
        });
    }

    private PlatformInboundMessage messageFor(EventProcessor processor){
        if (processor instanceof TrackingEventProcessor)
            return new TrackingEventProcessorInfoMessage((TrackingEventProcessor) processor);
        if (processor instanceof SubscribingEventProcessor)
            return new SubscribingEventProcessorInfoMessage((SubscribingEventProcessor) processor);
        throw new RuntimeException("Unknown instance of Event Processor detected.");
    }

}
