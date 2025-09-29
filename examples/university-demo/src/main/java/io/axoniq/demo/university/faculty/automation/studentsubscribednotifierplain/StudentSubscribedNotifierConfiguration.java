package io.axoniq.demo.university.faculty.automation.studentsubscribednotifierplain;

import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.QualifiedName;

public class StudentSubscribedNotifierConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        PooledStreamingEventProcessorModule automationProcessor = EventProcessorModule
                .pooledStreaming("Automation_WhenStudentSubscribedThenSendNotification_Processor")
                .eventHandlingComponents(
                        c -> c.declarative(cfg -> new SimpleEventHandlingComponent(
                                new PropertySequencingPolicy<StudentSubscribedToCourse, StudentId>(
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
