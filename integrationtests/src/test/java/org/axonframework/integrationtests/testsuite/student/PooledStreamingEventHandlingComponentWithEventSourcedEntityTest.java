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
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.eventsourcing.annotation.reflection.InjectEntityId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.integrationtests.testsuite.student.commands.SendMaxCoursesNotificationCommand;
import org.axonframework.integrationtests.testsuite.student.events.MaxCoursesNotificationSentEvent;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class PooledStreamingEventHandlingComponentWithEventSourcedEntityTest extends AbstractStudentTestSuite {

    @EventSourcedEntity(tagKey = "Student")
    record StudentCoursesAutomationState(String studentId, List<String> courses, boolean notified) {

        @EntityCreator
        StudentCoursesAutomationState(@InjectEntityId String studentId) {
            this(studentId, List.of(), false);
        }

        @EventSourcingHandler
        StudentCoursesAutomationState evolve(StudentEnrolledEvent event) {
            var updatedCourses = new java.util.ArrayList<>(courses);
            updatedCourses.add(event.courseId());
            return new StudentCoursesAutomationState(studentId, updatedCourses, false);
        }

        @EventSourcingHandler
        StudentCoursesAutomationState evolve(MaxCoursesNotificationSentEvent event) {
            return new StudentCoursesAutomationState(studentId, courses, true);
        }
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        EventSourcedEntityModule<String, StudentCoursesAutomationState> studentCoursesEntity =
                EventSourcedEntityModule.annotated(String.class, StudentCoursesAutomationState.class);
        configurer.componentRegistry(cr -> cr.registerModule(studentCoursesEntity));

        CommandHandlingModule sendMaxCoursesNotificationCommandHandler = CommandHandlingModule
                .named("send-max-courses-notification-command-handler")
                .commandHandlers()
                .commandHandler(new QualifiedName(SendMaxCoursesNotificationCommand.class), (c, ctx) -> {
                    var stateManager = ctx.component(StateManager.class);
                    var converter = ctx.component(Converter.class);
                    var command = c.payloadAs(SendMaxCoursesNotificationCommand.class, converter);
                    var studentId = command.studentId();
                    var state = stateManager.loadEntity(StudentCoursesAutomationState.class, studentId, ctx).join();
                    var canNotify = state != null && !state.notified();
                    if (canNotify) {
                        var eventAppender = EventAppender.forContext(ctx);
                        eventAppender.append(new MaxCoursesNotificationSentEvent(studentId));
                    }
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT);
                }).build();

        configurer.registerCommandHandlingModule(sendMaxCoursesNotificationCommandHandler);

//        var studentRegisteredCoursesProcessor = EventProcessorModule
//                .pooledStreaming("when-student-enrolled-to-max-courses-then-send-notification")
//                .eventHandlingComponents(components -> components.declarative(
//                        cfg -> whenStudentEnrolledToMaxCoursesThenSendNotificationAutomation()
//                )).notCustomized();
        var studentRegisteredCoursesProcessor = EventProcessorModule
                .pooledStreaming("when-student-enrolled-to-max-courses-then-send-notification")
                .eventHandlingComponents(components -> components.annotated(
                        cfg -> new WhenStudentEnrolledToMaxCoursesThenSendNotificationAutomation()
                )).notCustomized();
        configurer.messaging(
                messaging -> messaging.eventProcessing(
                        ep -> ep.pooledStreaming(
                                ps -> ps.defaults((cfg, d) -> d.eventSource(
                                        (StreamableEventSource<? extends EventMessage<?>>) cfg.getComponent(EventStore.class))
                                )
                        )
                )
        );
        return configurer.messaging(
                messaging -> messaging.eventProcessing(
                        ep -> ep.pooledStreaming(
                                ps -> ps.processor(studentRegisteredCoursesProcessor)
                        )
                )
        );
    }


    @Test
    void sample() {
        // given
        startApp();

        // when
        var studentId = "student-id-1";
        studentEnrolledToCourse(studentId, "my-courseId-1");
        studentEnrolledToCourse(studentId, "my-courseId-2");
        studentEnrolledToCourse(studentId, "my-courseId-3");

        // then
        await().atMost(2, TimeUnit.SECONDS)
               .untilAsserted(() -> verifyNotificationSentTo(studentId));
    }

    @Nonnull
    private static EventHandlingComponent whenStudentEnrolledToMaxCoursesThenSendNotificationAutomation() {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(
                new QualifiedName(StudentEnrolledEvent.class),
                (event, context) -> {
                    var converter = context.component(Converter.class);
                    var studentEnrolled = event.payloadAs(StudentEnrolledEvent.class, converter);
                    var studentId = studentEnrolled.studentId();
                    var state = context.component(StateManager.class);
                    // todo: I have null here!
                    var loadedState = state.loadEntity(StudentCoursesAutomationState.class,
                                                       studentId,
                                                       context).join();
                    var readModel = loadedState != null ? loadedState : new StudentCoursesAutomationState(studentId);
                    if (readModel.courses.size() >= 3) {
                        var commandGateway = context.component(CommandGateway.class);
                        var cr = commandGateway.send(new SendMaxCoursesNotificationCommand(studentId), context);
                        return MessageStream.fromFuture(cr.getResultMessage()).ignoreEntries().cast();
                    }
                    return MessageStream.empty();
                }
        );
        return eventHandlingComponent;
    }

    static class WhenStudentEnrolledToMaxCoursesThenSendNotificationAutomation {

        @EventHandler
        public MessageStream.Empty<?> react(
                StudentEnrolledEvent event, // why is not converted?
//                @InjectEntity StudentCoursesAutomationState state,
                ProcessingContext context
        ) {
            var studentId = event.studentId();
//            var readModel = state != null ? state : new StudentCoursesAutomationState(studentId);
//            if (readModel.courses.size() >= 3 && !readModel.notified()) {
//                var commandGateway = context.component(CommandGateway.class);
//                commandGateway.send(new SendMaxCoursesNotificationCommand(studentId), context);
//            }
            return MessageStream.empty();
        }
    }

    protected void studentEnrolledToCourse(String studentId, String courseId) {
        eventOccurred(StudentEnrolledEvent.class, new StudentEnrolledEvent(studentId, courseId));
    }

    protected <T> void eventOccurred(Class<T> clazz, T payload) {
        var uow = unitOfWorkFactory.create();
        var eventMessage = new GenericEventMessage<T>(new MessageType(clazz), payload);
        uow.runOnInvocation(
                context -> context.component(EventStore.class)
                                  .transaction(context)
                                  .appendEvent(eventMessage)
        );
        uow.execute().join();
    }

    protected void verifyNotificationSentTo(String studentId) {
        UnitOfWork uow = unitOfWorkFactory.create();
        uow.executeWithResult(context -> context.component(StateManager.class)
                                                .repository(StudentCoursesAutomationState.class, String.class)
                                                .load(studentId, context)
                                                .thenAccept(student -> assertTrue(student.entity().notified()))).join();
    }
}
