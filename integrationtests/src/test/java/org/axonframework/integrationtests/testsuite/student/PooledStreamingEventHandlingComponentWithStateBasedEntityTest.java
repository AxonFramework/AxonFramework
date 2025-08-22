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
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.configuration.StateBasedEntityModule;
import org.axonframework.modelling.repository.InMemoryRepository;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class PooledStreamingEventHandlingComponentWithStateBasedEntityTest extends AbstractStudentTestSuite {


    private final List<String> notificationSentToStudents = new CopyOnWriteArrayList<>();

    record StudentCoursesReadModel(String studentId, List<String> courses) {

        StudentCoursesReadModel(String studentId) {
            this(studentId, List.of());
        }

        StudentCoursesReadModel evolve(StudentEnrolledEvent event) {
            return new StudentCoursesReadModel(studentId, List.of(event.courseId()));
        }
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        var studentCoursesRepository = new InMemoryRepository<>(String.class, StudentCoursesReadModel.class);
        StateBasedEntityModule<String, StudentCoursesReadModel> studentCoursesEntity =
                StateBasedEntityModule.declarative(String.class, StudentCoursesReadModel.class)
                                      .repository(cfg -> studentCoursesRepository)
                                      .messagingModel((configuration, builder) -> builder.build())
                                      .entityIdResolver(cfg -> (entity, context) -> "student-1");
        configurer.componentRegistry(cr -> cr.registerModule(studentCoursesEntity));

        var studentRegisteredCoursesProcessor = EventProcessorModule
                .pooledStreaming("student-courses-readmodel-processor")
                .eventHandlingComponents(components -> components.declarative(
                        cfg -> studentCoursesProjector()
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
               .untilAsserted(() -> assertThat(notificationSentToStudents).containsAnyOf(studentId));
    }

    @Nonnull
    private static EventHandlingComponent studentCoursesProjector() {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(
                new QualifiedName(StudentEnrolledEvent.class),
                (event, context) -> {
                    var converter = context.component(Converter.class);
                    var studentEnrolled = event.payloadAs(StudentEnrolledEvent.class, converter);
                    var studentId = studentEnrolled.studentId();
                    var state = context.component(StateManager.class);
                    // todo: I have null here!
                    var loadedState = state.loadEntity(StudentCoursesReadModel.class,
                                                        studentId,
                                                        context).join();
                    var readModel = loadedState != null ? loadedState : new StudentCoursesReadModel(studentId);
                    readModel.evolve(studentEnrolled);
                    return MessageStream.empty();
                }
        );
        return eventHandlingComponent;
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
        uow.runOnInvocation(context -> context.component(EventStore.class).transaction(context)
                                              .appendEvent(eventMessage));
        uow.execute().join();
    }
}
