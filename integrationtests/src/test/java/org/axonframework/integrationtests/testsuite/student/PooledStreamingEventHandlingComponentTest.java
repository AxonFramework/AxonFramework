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
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.*;
import org.springframework.core.convert.converter.Converter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class PooledStreamingEventHandlingComponentTest extends AbstractStudentTestSuite {


    private final List<String> notificationSentToStudents = new CopyOnWriteArrayList<>();

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
               .untilAsserted(() -> assertThat(notificationSentToStudents).containsAnyOf(studentId));
    }


    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        configurer.modelling(
                modelling -> modelling.registerCommandHandlingModule(
                        sendMaxCoursesEnrolledNotificationCommandHandler()
                )
        );

        var studentRegisteredCoursesProcessor = EventProcessorModule
                .pooledStreaming("student-registered-courses-processor")
                .eventHandlingComponents(components -> components.declarative(
                        cfg -> studentMaxCoursesEnrolledNotifier(cfg.getComponent(StateManager.class),
                                                                 cfg.getComponent(CommandGateway.class))
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

    private CommandHandlingModule.CommandHandlerPhase sendMaxCoursesEnrolledNotificationCommandHandler() {
        return CommandHandlingModule
                .named("student-max-courses-notifier")
                .commandHandlers()
                .commandHandler(
                        new QualifiedName(SendMaxCoursesEnrolledNotificationCommand.class),
                        (command, context) -> {
//                            SendMaxCoursesEnrolledNotificationCommand payload = (SendMaxCoursesEnrolledNotificationCommand) command.payload();
//                            var studentId = payload.studentId();
                            var studentId = "student-id-1";
                            notificationSentToStudents.add(studentId);
                            return MessageStream.just(SUCCESSFUL_COMMAND_RESULT);
                        }
                );
    }

    @Nonnull
    private static EventHandlingComponent studentMaxCoursesEnrolledNotifier(
            StateManager stateManager,
            CommandGateway commandGateway
    ) {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(
                new QualifiedName(StudentEnrolledEvent.class),
                (event, context) -> {
                    var converter = context.component(Converter.class);
//                    StudentEnrolledEvent payload = (StudentEnrolledEvent) event.payload();
//                    var studentId = payload.studentId();
                    var studentId = "student-id-1";
                    var state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, studentId, context).join();
                    if (student.getCoursesEnrolled().size() == 3) {
                        commandGateway.sendAndWait(new SendMaxCoursesEnrolledNotificationCommand(studentId));
                    }
                    return MessageStream.empty();
                }
        );
        return eventHandlingComponent;
    }

    record SendMaxCoursesEnrolledNotificationCommand(String studentId) {

    }

    protected void studentEnrolledToCourse(String studentId, String courseId) {
        storeEvent(StudentEnrolledEvent.class, new StudentEnrolledEvent(studentId, courseId));
    }

    protected <T> void storeEvent(Class<T> clazz, T payload) {
        UnitOfWork uow = unitOfWorkFactory.create();
        var eventMessage = new GenericEventMessage<T>(
                new MessageType(clazz),
                payload
        );
        uow.runOnInvocation(context -> context.component(EventStore.class).transaction(context).appendEvent(eventMessage));
        uow.execute().join();
    }

}
