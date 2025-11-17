package io.axoniq.demo.university.faculty.automation.studentsubscribednotifier;

import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

import java.util.concurrent.CompletableFuture;

public class StudentSubscribedNotifierConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        PooledStreamingEventProcessorModule automationProcessor = EventProcessorModule
                .pooledStreaming("Automation_WhenStudentSubscribedThenSendNotification_Processor")
                .eventHandlingComponents(
                        c -> c.autodetected(cfg -> new WhenStudentSubscribedThenSendNotification(cfg.getComponent(NotificationService.class)))
                )
                // Due to a minor bug in the InMemoryEventStorageEngine this customization is needed if you want to use the implementation in the tests
                .customized((c, cus) -> cus.initialToken(s -> CompletableFuture.completedFuture(new GlobalSequenceTrackingToken(0))));

        return configurer
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                        eventProcessing.pooledStreaming(ps -> ps.processor(automationProcessor))
                )));
    }

}
