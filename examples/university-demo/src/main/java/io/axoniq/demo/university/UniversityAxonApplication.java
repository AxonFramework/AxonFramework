package io.axoniq.demo.university;

import io.axoniq.demo.university.faculty.FacultyModuleConfiguration;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class UniversityAxonApplication {

    public ApplicationConfigurer<?> configurer() {
        var configurer = EventSourcingConfigurer.create();
        configurer = FacultyModuleConfiguration.configure(configurer);
        return configurer;
    }

    public static void main(String[] args) {
        var configurer = new UniversityAxonApplication()
                .configurer();
        configurer.start();
    }
}
