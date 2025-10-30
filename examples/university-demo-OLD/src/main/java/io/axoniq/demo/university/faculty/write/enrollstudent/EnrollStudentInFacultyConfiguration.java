package io.axoniq.demo.university.faculty.write.enrollstudent;

import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class EnrollStudentInFacultyConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        return configurer
                .registerEntity(EventSourcedEntityModule.annotated(StudentId.class, EnrollStudentInFacultyCommandHandler.Student.class))
                .registerCommandHandlingModule(CommandHandlingModule.named("EnrollStudentInFaculty")
                        .commandHandlers()
                        .annotatedCommandHandlingComponent(c -> new EnrollStudentInFacultyCommandHandler())
                );
    }
}
