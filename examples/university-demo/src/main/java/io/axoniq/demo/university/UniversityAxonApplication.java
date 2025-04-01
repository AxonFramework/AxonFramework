package io.axoniq.demo.university;

import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class UniversityAxonApplication {

    public ApplicationConfigurer<?> configurer() {
        var configurer = EventSourcingConfigurer.create();
    }

    public static void main(String[] args) {
        var configurer = new UniversityAxonApplication()
                .configurer();
        configurer.start();
    }
}
