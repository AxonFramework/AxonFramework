package org.axonframework.examples.university.faculty;

import org.axonframework.examples.university.ConfigurationProperties;
import org.axonframework.examples.university.FacultyModuleConfiguration;
import org.axonframework.examples.university.UniversityExampleApplication;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.server.AxonServerContainerUtils;

import java.io.IOException;
import java.util.function.UnaryOperator;

public class FacultyAxonTestFixture {

    public static AxonTestFixture app() {
        return slice(FacultyModuleConfiguration::configure);
    }

    public static AxonTestFixture slice(UnaryOperator<EventSourcingConfigurer> customization) {
        var application = new UniversityExampleApplication();
        var configuration = ConfigurationProperties.load();
        var configurer = application.configurer(configuration, customization);
        purgeAxonServerIfEnabled(configuration);
        return AxonTestFixture.with(configurer, c -> configuration.axonServerEnabled() ? c : c.disableAxonServer());
    }

    private static void purgeAxonServerIfEnabled(ConfigurationProperties configuration) {
        boolean axonServerEnabled = configuration.axonServerEnabled();
        if (axonServerEnabled) {
            try {
                AxonServerContainerUtils.purgeEventsFromAxonServer("localhost", 8024, "default", true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private FacultyAxonTestFixture() {

    }
}
