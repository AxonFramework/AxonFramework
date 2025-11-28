package org.axonframework.examples.university.automation;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.modelling.configuration.EntityModule;

import java.util.concurrent.CompletableFuture;

public class CourseFullyBookedNotifierConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        EntityModule<CourseId, WhenCourseFullyBookedThenSendNotification.State> automationState =
                EventSourcedEntityModule.autodetected(CourseId.class,
                                                      WhenCourseFullyBookedThenSendNotification.State.class);

        PooledStreamingEventProcessorModule automationProcessor = EventProcessorModule
                .pooledStreaming("Automation_WhenCourseFullyBookedThenSendNotification_Processor")
                .eventHandlingComponents(
                        c -> c.autodetected(cfg -> new WhenCourseFullyBookedThenSendNotification())
                )
                // Due to a minor bug in the InMemoryEventStorageEngine this customization is needed if you want to use the implementation in the tests
                .customized((c, cus) -> cus.initialToken(s -> CompletableFuture.completedFuture(new GlobalSequenceTrackingToken(
                        0))));

        var commandHandlingModule = CommandHandlingModule.named("SendCoursFullyBookedCommandHandler")
                                                         .commandHandlers()
                                                         .annotatedCommandHandlingComponent(cfg -> new WhenCourseFullyBookedThenSendNotification())
                                                         .build();

        return configurer
                .registerEntity(automationState)
                .registerCommandHandlingModule(commandHandlingModule)
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                                                                                                           eventProcessing.pooledStreaming(
                                                                                                                   ps -> ps.processor(
                                                                                                                           automationProcessor))
                )));
    }
}
