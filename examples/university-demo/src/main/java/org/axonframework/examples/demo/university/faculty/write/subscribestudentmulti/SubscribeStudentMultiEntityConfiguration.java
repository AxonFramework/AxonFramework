package org.axonframework.examples.demo.university.faculty.write.subscribestudentmulti;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class SubscribeStudentMultiEntityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var courseEntity = EventSourcedEntityModule
                .autodetected(CourseId.class, Course.class);
        var studentEntity = EventSourcedEntityModule
                .autodetected(StudentId.class, Student.class);

        var commandHandlingModule = CommandHandlingModule
                .named("SubscribeStudentMulti")
                .commandHandlers()
                .autodetectedCommandHandlingComponent(c -> new SubscribeStudentToCourseCommandHandler());

        return configurer
                .registerEntity(courseEntity)
                .registerEntity(studentEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private SubscribeStudentMultiEntityConfiguration() {
        // Prevent instantiation
    }

}
