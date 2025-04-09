package io.axoniq.demo.university;

import io.axoniq.demo.university.faculty.FacultyModuleConfiguration;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.createcourseplain.CreateCourse;
import io.axoniq.demo.university.faculty.write.renamecourse.RenameCourse;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.infra.FilesystemStyleComponentDescriptor;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

import java.util.logging.Logger;

public class UniversityAxonApplication {

    private static final Logger logger = Logger.getLogger(UniversityAxonApplication.class.getName());

    public static void main(String[] args) {
        var configuration = startApplication();
        printApplicationConfiguration(configuration);
        executeSampleCommands(configuration);
    }

    private static AxonConfiguration startApplication() {
        var configurer = new UniversityAxonApplication().configurer();
        return configurer.start();
    }

    private static void printApplicationConfiguration(AxonConfiguration configuration) {
        var componentDescriptor = new FilesystemStyleComponentDescriptor();
        componentDescriptor.describeProperty("configuration", configuration);
        logger.info("Application started with following configuration: \n" + componentDescriptor.describe());
    }

    public ApplicationConfigurer configurer() {
        var configurer = EventSourcingConfigurer.create();
        configurer = FacultyModuleConfiguration.configure(configurer);
        return configurer;
    }

    private static void executeSampleCommands(AxonConfiguration configuration) {
        var courseId = CourseId.random();
        var createCourse = new CreateCourse(courseId, "Event Sourcing in Practice", 3);
        var renameCourse = new RenameCourse(courseId, "Advanced Event Sourcing");

        var commandGateway = configuration.getComponent(CommandGateway.class);
        commandGateway.sendAndWait(createCourse);
        commandGateway.sendAndWait(renameCourse);
    }
}
