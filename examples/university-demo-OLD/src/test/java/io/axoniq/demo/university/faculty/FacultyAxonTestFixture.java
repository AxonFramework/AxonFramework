package io.axoniq.demo.university.faculty;

import io.axoniq.demo.university.ConfigurationProperties;
import io.axoniq.demo.university.UniversityAxonApplication;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.fixture.RecordingEventStore;

import java.util.function.UnaryOperator;

/**
 * Due to some bugs in the Milestone 3 release, the test fixture only works without AxonServer (using InMemoryEventStorageEngine)
 * and requires the {@link FacultyAxonTestFixture#useRecordingEventStoreAsStreamableEventSource} to do not be broken by EventProcessor.
 */
public class FacultyAxonTestFixture {

    public static AxonTestFixture app() {
        return slice(FacultyModuleConfiguration::configure);
    }

    public static AxonTestFixture slice(UnaryOperator<EventSourcingConfigurer> customization) {
        var application = new UniversityAxonApplication();
        // fixme: Milestone 3 AxonTestFixture doesn't work with AxonServer
        var configurer = application.configurer(ConfigurationProperties.load().axonServerEnabled(false), customization);
        useRecordingEventStoreAsStreamableEventSource(configurer);
        return AxonTestFixture.with(configurer);
    }

    public static void useRecordingEventStoreAsStreamableEventSource(EventSourcingConfigurer configurer) {
        configurer.componentRegistry(cr -> cr.registerComponent(StreamableEventSource.class, c -> {
            var eventStore = (RecordingEventStore) c.getComponent(EventStore.class);
            try {
                return ReflectionUtils.getFieldValue(
                        RecordingEventStore.class.getSuperclass().getDeclaredField("delegate"),
                        eventStore
                );
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private FacultyAxonTestFixture() {

    }
}
