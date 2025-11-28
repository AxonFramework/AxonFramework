package org.axonframework.examples.university.write.enrollstudent;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

public class EnrollStudentInFacultyConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        return configurer
                .registerEntity(EventSourcedEntityModule.autodetected(String.class,
                                                                      EnrollStudentInFacultyCommandHandler.Student.class))
                .registerCommandHandlingModule(CommandHandlingModule.named("EnrollStudentInFaculty")
                                                                    .commandHandlers()
                                                                    .annotatedCommandHandlingComponent(c -> new EnrollStudentInFacultyCommandHandler())
                );
    }

    private EnrollStudentInFacultyConfiguration() {
        // prevent instantiation
    }
}
