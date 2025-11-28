package org.axonframework.examples.university.write.renamecourse;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.examples.university.shared.CourseId;

public class RenameCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(CourseId.class, CourseRenaming.class);
        return configurer
                .registerEntity(stateEntity);
    }

    private RenameCourseConfiguration() {
        // Prevent instantiation
    }
}
