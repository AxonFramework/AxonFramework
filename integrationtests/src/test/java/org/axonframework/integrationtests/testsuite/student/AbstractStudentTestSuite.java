/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.testsuite.student;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.integrationtests.testsuite.AbstractAxonServerIntegrationTest;
import org.axonframework.integrationtests.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.state.Course;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.function.Consumer;

import static org.axonframework.configuration.MessagingConfigurationDefaults.EVENT_CONVERTER_NAME;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sets up the basics for the testsuite of the Student/Mentor/Course model.
 * <p>
 * Can be customized by overriding the relevant methods. By default, uses a mix of different available options to
 * validate the different ways of setting up the event sourcing repository.
 * <p>
 * When using this test suite, be sure to invoke {@link #startApp()} when the test is all set.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 */
public abstract class AbstractStudentTestSuite extends AbstractAxonServerIntegrationTest {

    protected static final GenericCommandResultMessage<String> SUCCESSFUL_COMMAND_RESULT =
            new GenericCommandResultMessage<>(new MessageType("empty"), "successful");

    protected CommandGateway commandGateway;
    protected StateManager stateManager;

    private StatefulCommandHandlingModule.CommandHandlerPhase statefulCommandHandlingModule;
    private EventSourcedEntityModule<String, Course> courseEntity;
    private EventSourcedEntityModule<String, Student> studentEntity;

    @BeforeEach
    protected void prepareModule() {
        studentEntity = EventSourcedEntityModule
                .declarative(String.class, Student.class)
                .messagingModel((c, b) -> b
                        .entityEvolver(studentEvolver(c))
                        .build())
                .entityFactory(c -> EventSourcedEntityFactory.fromIdentifier(Student::new))
                .criteriaResolver(this::studentCriteriaResolver)
                .build();

        courseEntity = EventSourcedEntityModule
                .declarative(String.class, Course.class)
                .messagingModel((c, b) -> b
                        .entityEvolver(courseEvolver(c))
                        .build())
                .entityFactory(c -> EventSourcedEntityFactory.fromIdentifier(Course::new))
                .criteriaResolver(this::courseCriteriaResolver)
                .build();

        statefulCommandHandlingModule = StatefulCommandHandlingModule
                .named("student-course-module")
                .entities()
                .entity(studentEntity)
                .entity(courseEntity)
                .entities(this::registerAdditionalEntities)
                .commandHandlers();
    }

    @Override
    protected ApplicationConfigurer createConfigurer() {
        return EventSourcingConfigurer.create()
                                      .registerStatefulCommandHandlingModule(statefulCommandHandlingModule);
    }

    /**
     * Test suite implementations can invoke this method to register additional command handlers.
     *
     * @param handlerConfigurer The command handler phase of the {@link StatefulCommandHandlingModule}, allowing for
     *                          command handler registration.
     */
    protected void registerCommandHandlers(
            @Nonnull Consumer<StatefulCommandHandlingModule.CommandHandlerPhase> handlerConfigurer
    ) {
        statefulCommandHandlingModule.commandHandlers(handlerConfigurer);
    }

    /**
     * Starts the Axon Framework application.
     */
    protected void startApp() {
        super.startApp();
        commandGateway = startedConfiguration.getComponent(CommandGateway.class);

        Configuration moduleConfig = startedConfiguration.getModuleConfigurations().getFirst();
        stateManager = moduleConfig.getComponent(StateManager.class);
    }

    /**
     * Test suites can override this method to register additional entities.
     *
     * @param entityConfigurer The entity phase of the {@link StatefulCommandHandlingModule}, allowing for additional
     *                         entities to be registered.
     */
    protected void registerAdditionalEntities(StatefulCommandHandlingModule.EntityPhase entityConfigurer) {
        // Do nothing by default.
    }

    /**
     * Returns the {@link EntityEvolver} for the {@link Course} model. Defaults to manually calling the event sourcing
     * handlers on the model.
     */
    protected EntityEvolver<Course> courseEvolver(@Nonnull Configuration config) {
        return (course, event, context) -> {
            if (event.type().name().equals(StudentEnrolledEvent.class.getName())) {
                // Convert the payload to the expected type
                Converter converter = config.getComponent(Converter.class);
                StudentEnrolledEvent convert = converter.convert(event.payload(), StudentEnrolledEvent.class);
                Objects.requireNonNull(convert, "The converted payload must not be null.");
                course.handle(convert);
            }
            return course;
        };
    }

    /**
     * Returns the {@link CriteriaResolver} for the {@link Course} model. Defaults to a criteria that matches any event
     * with the tag "Course" and the given model id.
     */
    protected CriteriaResolver<String> courseCriteriaResolver(Configuration config) {
        return (courseId, ctx) -> EventCriteria.havingTags(new Tag("Course", courseId));
    }

    /**
     * Returns the {@link CriteriaResolver} for the {@link Student} model. Defaults to a criteria that matches any event
     * with the tag "Student" and the given model id.
     */
    protected CriteriaResolver<String> studentCriteriaResolver(Configuration config) {
        return (studentId, ctx) -> EventCriteria.havingTags(new Tag("Student", studentId));
    }

    /**
     * Returns the {@link EntityEvolver} for the {@link Student} model. Defaults to using the
     * {@link AnnotationBasedEntityEvolvingComponent} to use the annotations placed.
     */
    protected EntityEvolver<Student> studentEvolver(Configuration config) {
        return new AnnotationBasedEntityEvolvingComponent<>(
                Student.class,
                config.getComponent(Converter.class, EVENT_CONVERTER_NAME),
                config.getComponent(MessageTypeResolver.class)
        );
    }

    protected void changeStudentName(String studentId, String name) {
        sendCommand(new ChangeStudentNameCommand(studentId, name));
    }

    protected void enrollStudentToCourse(String studentId, String courseId) {
        sendCommand(new EnrollStudentToCourseCommand(studentId, courseId));
    }

    protected <T> void sendCommand(T payload) {
        commandGateway.sendAndWait(payload);
    }

    protected <T, R> R sendCommand(T payload, Class<R> expectedResultType) {
        return commandGateway.sendAndWait(payload, expectedResultType);
    }

    protected void verifyStudentName(String id, String name) {
        UnitOfWork uow = new UnitOfWork();
        uow.executeWithResult(
                   context -> stateManager.repository(Student.class, String.class)
                                          .load(id, context)
                                          .thenAccept(student -> assertEquals(name, student.entity().getName()))
           )
           .join();
    }

    protected void verifyStudentEnrolledInCourse(String id, String courseId) {
        UnitOfWork uow = new UnitOfWork();
        uow.executeWithResult(
                   context -> stateManager.repository(Student.class, String.class)
                                          .load(id, context)
                                          .thenAccept(student -> assertTrue(student.entity()
                                                                                   .getCoursesEnrolled()
                                                                                   .contains(courseId)))
                                          .thenCompose(v -> stateManager.repository(Course.class, String.class)
                                                                        .load(courseId, context))
                                          .thenAccept(course -> assertTrue(course.entity()
                                                                                 .getStudentsEnrolled()
                                                                                 .contains(id)))
           )
           .join();
    }
}
