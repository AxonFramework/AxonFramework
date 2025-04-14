package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class SubscribeStudentMultiEntityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var courseEntity = EventSourcedEntityBuilder
                .annotatedEntity(CourseId.class, Course.class);
        var studentEntity = EventSourcedEntityBuilder
                .annotatedEntity(StudentId.class, Student.class);

        var commandHandlingModule = StatefulCommandHandlingModule
                .named("SubscribeStudentMulti")
                .entities()
                .entity(courseEntity)
                .entity(studentEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new SubscribeStudentToCourseCommandHandler());
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

}
