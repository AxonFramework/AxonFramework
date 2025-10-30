package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.shared.ids.FacultyId;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.EntityModule;

import java.util.concurrent.CompletableFuture;

public class AllCoursesFullyBookedNotifierConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        EntityModule<FacultyId, WhenAllCoursesFullyBookedThenSendNotification.State> automationState =
                EventSourcedEntityModule.annotated(FacultyId.class, WhenAllCoursesFullyBookedThenSendNotification.State.class);

        PooledStreamingEventProcessorModule automationProcessor = EventProcessorModule
                .pooledStreaming("Automation_WhenAllCoursesFullyBookedThenSendNotification_Processor")
                .eventHandlingComponents(
                        c -> c.annotated(cfg -> new WhenAllCoursesFullyBookedThenSendNotification.AutomationEventHandler())
                )
                // Due to a minor bug in the InMemoryEventStorageEngine this customization is needed if you want to use the implementation in the tests
                .customized((c, cus) -> cus.initialToken(s -> CompletableFuture.completedFuture(new GlobalSequenceTrackingToken(0))));

        var commandHandlingModule = CommandHandlingModule.named("SendAllCoursesFullyBookedCommandHandler")
                .commandHandlers()
                .annotatedCommandHandlingComponent(cfg -> new WhenAllCoursesFullyBookedThenSendNotification.AutomationCommandHandler())
                .build();

        return configurer
                .registerEntity(automationState)
                .registerCommandHandlingModule(commandHandlingModule)
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                        eventProcessing.pooledStreaming(ps -> ps.processor(automationProcessor))
                )));
    }

}
