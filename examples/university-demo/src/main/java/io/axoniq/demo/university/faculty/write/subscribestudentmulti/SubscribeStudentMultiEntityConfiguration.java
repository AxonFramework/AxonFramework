package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class SubscribeStudentMultiEntityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var courseEntity = EventSourcedEntityModule
                .annotated(CourseId.class, Course.class);
        var studentEntity = EventSourcedEntityModule
                .annotated(StudentId.class, Student.class);

        var commandHandlingModule = StatefulCommandHandlingModule
                .named("SubscribeStudentMulti")
                .entities()
                .entity(courseEntity)
                .entity(studentEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new SubscribeStudentToCourseCommandHandler());
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private SubscribeStudentMultiEntityConfiguration() {
        // Prevent instantiation
    }

}
