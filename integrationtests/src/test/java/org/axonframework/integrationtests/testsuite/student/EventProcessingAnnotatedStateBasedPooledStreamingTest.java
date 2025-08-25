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
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.configuration.StateBasedEntityModule;
import org.axonframework.modelling.repository.InMemoryRepository;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class EventProcessingAnnotatedStateBasedPooledStreamingTest extends AbstractStudentTestSuite {

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
        verifyReadModelState(studentId, state -> {
            assertNotNull(state);
            assertThat(state.courses).containsExactly("my-courseId-1", "my-courseId-2", "my-courseId-3");
        });
    }

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
//        configurer.messaging(
//                messaging -> messaging.eventProcessing(
//                        ep -> ep.pooledStreaming(
//                                ps -> ps.defaults((cfg, d) -> d.eventSource(
//                                        (StreamableEventSource<? extends EventMessage<?>>) cfg.getComponent(EventStore.class))
//                                )
//                        )
//                )
//        );
        return configurer.messaging(
                messaging -> messaging.eventProcessing(
                        ep -> ep.pooledStreaming(
                                ps -> ps.processor(studentRegisteredCoursesProcessor)
                        )
                )
        );
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

    private void verifyReadModelState(String studentId, Consumer<StudentCoursesReadModel> stateVerifier) {
        await().atMost(5, TimeUnit.SECONDS)
                .pollDelay(1, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   System.out.println("CHECK!!!");
                   UnitOfWork uow = unitOfWorkFactory.create();
                   uow.executeWithResult(context -> context.component(StateManager.class)
                                                      .repository(StudentCoursesReadModel.class, String.class)
                                                      .load(studentId, context)
                                                      .thenAccept(student -> stateVerifier.accept(student.entity()))).join();
               });
    }
}
