package org.axonframework.examples.demo.university.faculty.automation.studentsubscribednotifierplain;

import org.axonframework.examples.demo.university.faculty.events.StudentSubscribedToCourse;
import org.axonframework.examples.demo.university.shared.ids.StudentId;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class StudentSubscribedNotifierConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        PooledStreamingEventProcessorModule automationProcessor = EventProcessorModule
                .pooledStreaming("Automation_WhenStudentSubscribedThenSendNotification_Processor")
                .eventHandlingComponents(
                        c -> c.declarative(cfg -> SimpleEventHandlingComponent.create(
                                "SimpleEventHandlingComponent",
                                new PropertySequencingPolicy<StudentSubscribedToCourse, StudentId, EventMessage>(
                                        StudentSubscribedToCourse.class,
                                        "studentId"
                                )
                        ).subscribe(new QualifiedName(StudentSubscribedToCourse.class), WhenStudentSubscribedThenSendNotification::react))
                ).notCustomized();

        return configurer
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                        eventProcessing.pooledStreaming(ps -> ps.processor(automationProcessor))
                )));
    }

}
