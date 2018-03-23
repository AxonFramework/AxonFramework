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

    //TODO Review
    private PlatformInboundMessage messageFor(EventProcessor processor){
        if (processor instanceof TrackingEventProcessor)
            return new TrackingEventProcessorInfoMessage((TrackingEventProcessor) processor);
        if (processor instanceof SubscribingEventProcessor)
            return new SubscribingEventProcessorInfoMessage((SubscribingEventProcessor) processor);
        return null;
    }

}
