package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class SubscribeStudentMultiEntityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var courseEntity = EventSourcedEntityModule
                .annotated(CourseId.class, Course.class);
        var studentEntity = EventSourcedEntityModule
                .annotated(StudentId.class, Student.class);

        var commandHandlingModule = CommandHandlingModule
                .named("SubscribeStudentMulti")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new SubscribeStudentToCourseCommandHandler());

        return configurer
                .registerEntity(courseEntity)
                .registerEntity(studentEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private SubscribeStudentMultiEntityConfiguration() {
        // Prevent instantiation
    }

}
