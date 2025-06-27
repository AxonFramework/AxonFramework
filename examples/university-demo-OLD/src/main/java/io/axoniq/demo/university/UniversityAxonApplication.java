package io.axoniq.demo.university;

import io.axoniq.demo.university.faculty.FacultyModuleConfiguration;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.faculty.write.createcourseplain.CreateCourse;
import io.axoniq.demo.university.faculty.write.renamecourse.RenameCourse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.infra.FilesystemStyleComponentDescriptor;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;

import java.util.logging.Level;
import java.util.logging.Logger;

public class UniversityAxonApplication {

    private static final String CONTEXT = "university";
    private static final Logger logger = Logger.getLogger(UniversityAxonApplication.class.getName());

    public static void main(String[] args) {
        ConfigurationProperties configProps = ConfigurationProperties.load();
        var configuration = startApplication(configProps);
        printApplicationConfiguration(configuration);
        executeSampleCommands(configuration);
    }

    private static AxonConfiguration startApplication(ConfigurationProperties configProps) {
        var configurer = new UniversityAxonApplication().configurer(configProps);
        return configurer.start();
    }

    private static void printApplicationConfiguration(AxonConfiguration configuration) {
        var componentDescriptor = new FilesystemStyleComponentDescriptor();
        componentDescriptor.describeProperty("configuration", configuration);
        logger.info("Application started with following configuration: \n" + componentDescriptor.describe());
    }

    public ApplicationConfigurer configurer() {
        return configurer(ConfigurationProperties.load());
    }

    public ApplicationConfigurer configurer(ConfigurationProperties configProps) {
        var configurer = EventSourcingConfigurer.create();
        if (configProps.axonServerEnabled) {
            configurer.componentRegistry(r -> r.registerComponent(AxonServerConfiguration.class, c -> {
                var axonServerConfig = new AxonServerConfiguration();
                axonServerConfig.setContext(CONTEXT);
                return axonServerConfig;
            }));
        } else {
            configurer = configurer.registerEventStorageEngine(c -> new InMemoryEventStorageEngine());
        }
        configurer = FacultyModuleConfiguration.configure(configurer);
        return configurer;
    }

    private static void executeSampleCommands(AxonConfiguration configuration) {
        try {
            var courseId = CourseId.random();
            var createCourse = new CreateCourse(courseId, "Event Sourcing in Practice", 3);
            var renameCourse = new RenameCourse(courseId, "Advanced Event Sourcing");

            var commandGateway = configuration.getComponent(CommandGateway.class);
            commandGateway.sendAndWait(createCourse);
            commandGateway.sendAndWait(renameCourse);
            logger.info("Successfully executed sample commands");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while executing sample commands: " + e.getMessage(), e);
        }
    }

}
