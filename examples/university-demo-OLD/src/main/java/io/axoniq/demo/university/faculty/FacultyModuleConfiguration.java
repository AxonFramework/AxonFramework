package io.axoniq.demo.university.faculty;

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class FacultyModuleConfiguration {

    public EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        return configurer;
    }
}
