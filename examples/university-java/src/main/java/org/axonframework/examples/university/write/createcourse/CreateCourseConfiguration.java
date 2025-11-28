package org.axonframework.examples.university.write.createcourse;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.examples.university.shared.CourseId;

public class CreateCourseConfiguration {

  public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
    var stateEntity = EventSourcedEntityModule
      .autodetected(CourseId.class, CourseCreation.class);

    return configurer
      .registerEntity(stateEntity);
  }

  private CreateCourseConfiguration() {
    // Prevent instantiation
  }

}
