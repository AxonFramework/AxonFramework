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

package org.axonframework.config;


import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.ModelContainer;
import org.axonframework.modelling.command.ModelIdResolver;
import org.axonframework.modelling.ModelRegistry;
import org.axonframework.modelling.SimpleModelRegistry;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.InjectModel;
import org.axonframework.modelling.command.annotation.InjectModelParameterResolverFactory;
import org.axonframework.modelling.command.annotation.TargetModelIdentifier;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests and examples for combining single and multi model command handling components. Models can be injected into
 * command handlers, and multiple models can be handled in a single command. Models can either be injected directly via
 * the {@link InjectModel} annotation, or via the {@link ModelContainer}. The identifier for the model can be resolved
 * via a custom {@link ModelIdResolver}, which is also shown in this test.
 */
class SingleAndMultiModelCommandHandlingComponentTest {

    private static final String DEFAULT_CONTEXT = "default";

    private final SimpleEventStore eventStore = new SimpleEventStore(
            new AsyncInMemoryEventStorageEngine(),
            DEFAULT_CONTEXT,
            new AnnotationBasedTagResolver()
    );

    private final EventStateApplier<Student> studentEventStateApplier = new AnnotationBasedEventStateApplier<>(Student.class);
    private final EventStateApplier<Course> courseEventStateApplier = (model, em, ctx) -> {
        if (em.getPayload() instanceof StudentEnrolledEvent e) {
            model.handle(e);
        }
        return model;
    };

    private final AsyncEventSourcingRepository<String, Student> studentRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Student", myModelId)),
            studentEventStateApplier,
            Student::new,
            DEFAULT_CONTEXT
    );


    private final AsyncEventSourcingRepository<String, Course> courseRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Course", myModelId)),
            courseEventStateApplier,
            Course::new,
            DEFAULT_CONTEXT
    );


    private final ModelRegistry registry = SimpleModelRegistry
            .create("MyModelRegistry")
            .registerModel(
                    String.class,
                    Student.class,
                    (id, context) -> studentRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
            )
            .registerModel(
                    String.class,
                    Course.class,
                    (id, context) -> courseRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
            );

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a singular model command.
     */
    @Test
    void canHandleSingularModelCommand() {
        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            Student student = model.getModel(Student.class, payload.id()).join();
                            appendEvent(context, new StudentNameChangedEvent(student.id, payload.name()));
                            return MessageStream.empty().cast();
                        });

        changeStudentName(component, "my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");
        changeStudentName(component, "my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");
        changeStudentName(component, "my-studentId-1", "name-3");
        verifyStudentName("my-studentId-1", "name-3");
        changeStudentName(component, "my-studentId-1", "name-4");
        verifyStudentName("my-studentId-1", "name-4");

        changeStudentName(component, "my-studentId-2", "name-5");
        verifyStudentName("my-studentId-1", "name-4");
        verifyStudentName("my-studentId-2", "name-5");
    }

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a singular model command.
     */
    @Test
    void canInjectStateIntoCommandHandlerViaParameter() {
        MultiModelAnnotatedCommandHandler handler = new MultiModelAnnotatedCommandHandler();

        var configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.getComponent(ModelRegistry.class)).thenReturn(registry);
        Mockito.when(configuration.getComponent(EventSink.class)).thenReturn(eventStore);

        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", registry)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        new MultiParameterResolverFactory(
                                ClasspathParameterResolverFactory.forClass(this.getClass()),
                                // To be able to get components
                                new ConfigurationParameterResolverFactory(configuration),
                                // To be able to get the model, the ModelRegistry needs to be available.
                                // When the new configuration API is there, we should have a way to resolve this
                                new InjectModelParameterResolverFactory(registry)
                        )));


        changeStudentName(component, "my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");


        changeStudentName(component, "my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");

        enrollStudentToCourse(component, "my-studentId-1", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-1", "my-courseId-1");
    }

    private void verifyStudentName(String id, String name) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult(context ->
                                      studentRepository
                                              .load(id, context)
                                              .thenAccept(student -> assertEquals(name, student.entity().name))
        ).join();
    }

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a command that targets multiple models at the
     * same time, in the same transaction.
     */
    @Test
    void canHandleCommandThatTargetsMultipleModels() {
        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            Student student = model.getModel(Student.class, payload.id()).join();
                            appendEvent(context, new StudentNameChangedEvent(student.id, payload.name()));
                            return MessageStream.empty().cast();
                        })
                .subscribe(
                        new QualifiedName(EnrollStudentToCourseCommand.class),
                        (command, models, context) -> {
                            EnrollStudentToCourseCommand payload = (EnrollStudentToCourseCommand) command.getPayload();
                            Student student = models.getModel(Student.class, payload.studentId()).join();
                            Course course = models.getModel(Course.class, payload.courseId()).join();

                            if (student.getCoursesEnrolled().size() > 2) {
                                throw new IllegalArgumentException(
                                        "Student already enrolled in 3 courses");
                            }

                            if (course.getStudentsEnrolled().size() > 2) {
                                throw new IllegalArgumentException("Course already has 3 students");
                            }
                            appendEvent(context, new StudentEnrolledEvent(payload.studentId(), payload.courseId()));
                            return MessageStream.empty().cast();
                        });

        // First student
        enrollStudentToCourse(component, "my-studentId-2", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Second student
        enrollStudentToCourse(component, "my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Third and last possible student
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Fourth can still enroll for other course
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-2");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-2");

        // But five can not enroll for the first course
        var exception = assertThrows(CompletionException.class,
                                     () -> enrollStudentToCourse(component, "my-studentId-5", "my-courseId-1"
                                     ));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Course already has 3 students"));
    }

    /**
     * Tests the injection of a compound model based on a compound identifier that loads events of two tags. Currently
     * is disabled, as two requirements are not met: - Can not append events to the store: "Conditions with more than
     * one tag are not yet supported" - The `EventCriteria` class does an AND on the tags, not an OR.
     * <p>
     * In time, I expect this test to work, and for now it serves as an example.
     */
    @Disabled
    @Test
    void canHandleCommandThatTargetsMultipleModelsViaInjectionOfSameType() {

        var configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.getComponent(ModelRegistry.class)).thenReturn(registry);
        Mockito.when(configuration.getComponent(EventSink.class)).thenReturn(eventStore);

        MultiModelAnnotatedCommandHandler handler = new MultiModelAnnotatedCommandHandler();
        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", registry)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        new MultiParameterResolverFactory(
                                ClasspathParameterResolverFactory.forClass(this.getClass()),
                                // To be able to get components
                                new ConfigurationParameterResolverFactory(configuration),
                                // To be able to get the model, the ModelRegistry needs to be available.
                                // When the new configuration API is there, we should have a way to resolve this
                                new InjectModelParameterResolverFactory(registry)
                        )));

        // Can assign mentor to mentee
        sendCommand(component, new AssignMentorCommand("my-studentId-1", "my-studentId-2"));

        // But not a second time
        var exception = assertThrows(CompletionException.class,
                                     () -> sendCommand(component,
                                                       new AssignMentorCommand("my-studentId-1", "my-studentId-3")
                                     ));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Mentor already assigned to a mentee"));
    }

    private void appendEvent(ProcessingContext context, Object event) {
        eventStore.transaction(context, DEFAULT_CONTEXT)
                  .appendEvent(new GenericEventMessage<>(
                          new MessageType(event.getClass()),
                          event));
    }

    private void verifyStudentEnrolledInCourse(String id, String courseId) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult(context -> studentRepository
                   .load(id, context)
                   .thenAccept(student -> assertTrue(student.entity().getCoursesEnrolled().contains(courseId)))
                   .thenCompose(v -> courseRepository.load(courseId, context))
                   .thenAccept(course -> assertTrue(course.entity().getStudentsEnrolled().contains(id))))
           .join();
    }

    private void changeStudentName(StatefulCommandHandlingComponent component, String id, String name) {
        sendCommand(component, new ChangeStudentNameCommand(id, name));
    }


    private void enrollStudentToCourse(
            StatefulCommandHandlingComponent component,
            String studentId,
            String courseId
    ) {
        sendCommand(component, new EnrollStudentToCourseCommand(studentId, courseId));
    }

    private <T> void sendCommand(
            StatefulCommandHandlingComponent component,
            T payload
    ) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult(context -> {
            GenericCommandMessage<T> command = new GenericCommandMessage<>(
                    new MessageType(payload.getClass()),
                    payload);
            return component.handle(command, context).first().asCompletableFuture();
        }).join();
    }


    record ChangeStudentNameCommand(
            @TargetModelIdentifier
            String id,
            String name
    ) {

    }

    record MentorModelIdentifier(
            String mentorId,
            String menteeId
    ) {

    }

    record AssignMentorCommand(
            String menteeId,
            String mentorId
    ) {

    }

    record EnrollStudentToCourseCommand(
            String studentId,
            String courseId
    ) {

    }

    record StudentNameChangedEvent(
            @EventTag(key = "Student")
            String id,
            String name
    ) {

    }

    record StudentEnrolledEvent(
            @EventTag(key = "Student")
            String studentId,
            @EventTag(key = "Course")
            String courseId
    ) {

    }

    record MentorAssignedToMenteeEvent(
            @EventTag(key = "Student")
            String mentorId,
            @EventTag(key = "Student")
            String menteeId
    ) {

    }

    /**
     * Event-sourced Student model
     */
    static class Student {

        private String id;
        private String name;
        private String mentorId;
        private String menteeId;
        private List<String> coursesEnrolled = new ArrayList<>();

        public Student(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getCoursesEnrolled() {
            return coursesEnrolled;
        }

        public String getMentorId() {
            return mentorId;
        }

        public String getMenteeId() {
            return menteeId;
        }

        @EventSourcingHandler
        public void handle(StudentEnrolledEvent event) {
            coursesEnrolled.add(event.courseId());
        }

        @EventSourcingHandler
        public void handle(StudentNameChangedEvent event) {
            name = event.name();
        }

        @EventSourcingHandler
        public void handle(MentorAssignedToMenteeEvent event) {
            if (event.mentorId().equals(this.id)) {
                // I have been assigned a mentee!
                this.menteeId = event.menteeId();
            } else if (event.menteeId().equals(this.id)) {
                // I have been assigned a mentor!
                this.mentorId = event.mentorId();
            }
        }
    }

    static class StudentMentorCompoundModel {

        private MentorModelIdentifier identifier;
        private boolean mentorHasMentee;
        private boolean menteeHasMentor;

        public StudentMentorCompoundModel(MentorModelIdentifier identifier) {
            this.identifier = identifier;
        }

        public boolean isMentorHasMentee() {
            return mentorHasMentee;
        }

        public boolean isMenteeHasMentor() {
            return menteeHasMentor;
        }

        @EventSourcingHandler
        public void handle(MentorAssignedToMenteeEvent event) {
            if (event.mentorId().equals(this.identifier.mentorId())) {
                mentorHasMentee = true;
            } else if (event.menteeId().equals(this.identifier.menteeId())) {
                menteeHasMentor = true;
            }
        }
    }

    static class Course {

        private String id;
        private List<String> studentsEnrolled = new ArrayList<>();

        public Course(String id) {
            this.id = id;
        }

        public List<String> getStudentsEnrolled() {
            return studentsEnrolled;
        }

        public void handle(StudentEnrolledEvent event) {
            studentsEnrolled.add(event.studentId());
        }

        @Override
        public String toString() {
            return "Course{" +
                    "id='" + id + '\'' +
                    ", studentsEnrolled=" + studentsEnrolled +
                    '}';
        }
    }

    static class MultiModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(
                ChangeStudentNameCommand command,
                @InjectModel Student student,
                EventSink eventSink,
                ProcessingContext context
        ) {
            // Change name through event
            eventSink.publish(context, DEFAULT_CONTEXT, new GenericEventMessage<>(
                    new MessageType(StudentNameChangedEvent.class),
                    new StudentNameChangedEvent(student.id, command.name)
            ));
            // Model through magic of repository automatically updated
            assertEquals(student.name, command.name);
        }

        @CommandHandler
        public void handle(EnrollStudentToCourseCommand command,
                           ModelContainer container,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
            Student student = container.getModel(Student.class, command.studentId()).join();

            if (student.getCoursesEnrolled().size() > 2) {
                throw new IllegalArgumentException("Student already enrolled in 3 courses");
            }

            // Lazy-loading, so only load course if the student is able to enroll
            Course course = container.getModel(Course.class, command.courseId()).join();
            if (course.getStudentsEnrolled().size() > 2) {
                throw new IllegalArgumentException("Course already has 3 students");
            }

            eventSink.publish(context, DEFAULT_CONTEXT, new GenericEventMessage<>(
                    new MessageType(StudentEnrolledEvent.class),
                    new StudentEnrolledEvent(command.studentId(), command.courseId())
            ));

            assertTrue(student.getCoursesEnrolled().contains(command.courseId()));
            assertTrue(course.getStudentsEnrolled().contains(command.studentId()));
        }


        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectModel(idResolver = MentorIdResolver.class) Student mentor,
                           @InjectModel(idProperty = "menteeId") Student mentee,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
            if (mentor.getMenteeId() != null) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (mentee.getMentorId() != null) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            eventSink.publish(context, DEFAULT_CONTEXT, new GenericEventMessage<>(
                    new MessageType(MentorAssignedToMenteeEvent.class),
                    new MentorAssignedToMenteeEvent(mentor.id, mentee.id)
            ));
        }

        public static class MentorIdResolver implements ModelIdResolver<String> {

            @Override
            public String resolve(@Nonnull Message<?> command, @Nonnull ProcessingContext context) {
                if (command.getPayload() instanceof AssignMentorCommand(String studentId, String mentorId)) {
                    return studentId;
                }
                return null;
            }

            public MentorIdResolver() {
            }
        }
    }
}