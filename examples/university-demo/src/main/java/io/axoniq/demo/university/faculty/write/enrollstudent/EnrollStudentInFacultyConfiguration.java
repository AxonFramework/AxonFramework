package io.axoniq.demo.university.faculty.write.enrollstudent;

import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

public class EnrollStudentInFacultyConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        return configurer
                .registerEntity(EventSourcedEntityModule.autodetected(StudentId.class, EnrollStudentInFacultyCommandHandler.Student.class))
                .registerCommandHandlingModule(CommandHandlingModule.named("EnrollStudentInFaculty")
                        .commandHandlers()
                        .annotatedCommandHandlingComponent(c -> new EnrollStudentInFacultyCommandHandler())
                );
    }
}
