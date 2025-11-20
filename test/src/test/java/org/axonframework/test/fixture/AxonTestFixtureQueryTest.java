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

package org.axonframework.test.fixture;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SimpleQueryHandlingComponent;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for query testing functionality in AxonTestFixture.
 * This tests the auto-await mechanism that allows queries to wait for async event processors
 * to update the read model before executing the query.
 */
public class AxonTestFixtureQueryTest {

    @Test
    void givenEventsWhenQueryThenExpectResult_Success() {
        var configurer = queryTestConfig();
        var fixture = AxonTestFixture.with(configurer);

        var studentId = "student-123";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "John Doe", 1);
        var expectedReadModel = new StudentReadModel(studentId, "John Doe");

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentById(studentId))
        .then()
               .expectResult(expectedReadModel);
    }

    @Test
    void givenEventsWhenQueryThenExpectResultSatisfies_Success() {
        var configurer = queryTestConfig();
        var fixture = AxonTestFixture.with(configurer);

        var studentId = "student-456";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "Jane Smith", 1);

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentById(studentId))
        .then()
               .expectResultSatisfies(result -> {
                   assertThat(result).isInstanceOf(StudentReadModel.class);
                   StudentReadModel student = (StudentReadModel) result;
                   assertThat(student.id()).isEqualTo(studentId);
                   assertThat(student.name()).isEqualTo("Jane Smith");
               });
    }

    @Test
    void givenNoEventsWhenQueryThenExpectNull_Success() {
        var configurer = queryTestConfig();
        var fixture = AxonTestFixture.with(configurer);

        var studentId = "non-existent-student";

        fixture.given()
        .when()
               .query(new GetStudentById(studentId), java.time.Duration.ZERO)
        .then()
               .expectResult(null);
    }

    @Test
    void givenEventsWhenQueryWithWrappedResultThenExpectResult_Success() {
        var configurer = queryWithWrappedResultConfig();
        var fixture = AxonTestFixture.with(configurer);

        var studentId = "student-789";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "Alice Johnson", 1);
        var expectedStudent = new StudentReadModel(studentId, "Alice Johnson");
        var expectedWrappedResult = new GetStudentResult(expectedStudent);

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentByIdWrapped(studentId))
        .then()
               .expectQueryResult(expectedWrappedResult);
    }

    @Test
    void givenEventsWhenQueryWithWrappedResultThenExpectResultSatisfies_Success() {
        var configurer = queryWithWrappedResultConfig();
        var fixture = AxonTestFixture.with(configurer);

        var studentId = "student-999";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "Bob Williams", 1);

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentByIdWrapped(studentId))
        .then()
               .expectQueryResultSatisfies(result -> {
                   assertThat(result).isInstanceOf(GetStudentResult.class);
                   GetStudentResult wrappedResult = (GetStudentResult) result;
                   assertThat(wrappedResult.student()).isNotNull();
                   assertThat(wrappedResult.student().id()).isEqualTo(studentId);
                   assertThat(wrappedResult.student().name()).isEqualTo("Bob Williams");
               });
    }

    private static EventSourcingConfigurer queryTestConfig() {
        // In-memory repository shared between projection and query handler
        StudentRepository repository = new InMemoryStudentRepository();

        var configurer = EventSourcingConfigurer.create();

        // Register the repository as a component
        configurer.componentRegistry(cr -> cr.registerComponent(
                StudentRepository.class,
                cfg -> repository
        ));

        // Configure async event processor for the projection
        configurer.messaging(cr -> cr.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(
                EventProcessorModule
                        .pooledStreaming("test-student-projection")
                        .eventHandlingComponents(c -> c.declarative(
                                cfg -> new SimpleEventHandlingComponent()
                                        .subscribe(
                                                new QualifiedName(StudentNameChangedEvent.class),
                                                (EventMessage e, ProcessingContext ctx) -> {
                                                    var payload = e.payloadAs(StudentNameChangedEvent.class);
                                                    var repo = ctx.component(StudentRepository.class);
                                                    repo.save(new StudentReadModel(payload.id(), payload.name()));
                                                    return MessageStream.empty();
                                                }
                                        )
                        )).notCustomized()
        ))));

        // Configure query handler
        QueryHandlingModule queryHandler = QueryHandlingModule.named("student-query-handler")
                .queryHandlers()
                .queryHandlingComponent(cfg -> {
                    var repo = cfg.getComponent(StudentRepository.class);
                    return SimpleQueryHandlingComponent
                            .create("student-queries")
                            .subscribe(
                                    new QualifiedName(GetStudentById.class),
                                    (org.axonframework.messaging.queryhandling.QueryMessage query, ProcessingContext context) -> {
                                        var payload = query.payloadAs(GetStudentById.class);
                                        var student = repo.findById(payload.id());
                                        var responseType = new MessageType(student != null ? student.getClass() : Object.class);
                                        var response = new GenericQueryResponseMessage(responseType, student);
                                        return MessageStream.fromItems(response);
                                    }
                            );
                })
                .build();

        configurer.registerQueryHandlingModule(queryHandler);

        return configurer;
    }

    private static EventSourcingConfigurer queryWithWrappedResultConfig() {
        // In-memory repository shared between projection and query handler
        StudentRepository repository = new InMemoryStudentRepository();

        var configurer = EventSourcingConfigurer.create();

        // Register the repository as a component
        configurer.componentRegistry(cr -> cr.registerComponent(
                StudentRepository.class,
                cfg -> repository
        ));

        // Configure async event processor for the projection (same as before)
        configurer.messaging(cr -> cr.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(
                EventProcessorModule
                        .pooledStreaming("test-student-projection-wrapped")
                        .eventHandlingComponents(c -> c.declarative(
                                cfg -> new SimpleEventHandlingComponent()
                                        .subscribe(
                                                new QualifiedName(StudentNameChangedEvent.class),
                                                (EventMessage e, ProcessingContext ctx) -> {
                                                    var payload = e.payloadAs(StudentNameChangedEvent.class);
                                                    var repo = ctx.component(StudentRepository.class);
                                                    repo.save(new StudentReadModel(payload.id(), payload.name()));
                                                    return MessageStream.empty();
                                                }
                                        )
                        )).notCustomized()
        ))));

        // Configure query handler that returns wrapped result
        QueryHandlingModule queryHandler = QueryHandlingModule.named("student-query-handler-wrapped")
                .queryHandlers()
                .queryHandlingComponent(cfg -> {
                    var repo = cfg.getComponent(StudentRepository.class);
                    return SimpleQueryHandlingComponent
                            .create("student-queries-wrapped")
                            .subscribe(
                                    new QualifiedName(GetStudentByIdWrapped.class),
                                    (org.axonframework.messaging.queryhandling.QueryMessage query, ProcessingContext context) -> {
                                        var payload = query.payloadAs(GetStudentByIdWrapped.class);
                                        var student = repo.findById(payload.id());
                                        var wrappedResult = new GetStudentResult(student);
                                        var responseType = new MessageType(wrappedResult.getClass());
                                        var response = new GenericQueryResponseMessage(responseType, wrappedResult);
                                        return MessageStream.fromItems(response);
                                    }
                            );
                })
                .build();

        configurer.registerQueryHandlingModule(queryHandler);

        return configurer;
    }

    // Test domain objects
    record GetStudentById(String id) {}
    record GetStudentByIdWrapped(String id) {}
    record StudentReadModel(String id, String name) {}
    record GetStudentResult(StudentReadModel student) {}

    // Simple in-memory repository
    interface StudentRepository {
        void save(StudentReadModel student);
        StudentReadModel findById(String id);
    }

    static class InMemoryStudentRepository implements StudentRepository {
        private final Map<String, StudentReadModel> store = new ConcurrentHashMap<>();

        @Override
        public void save(StudentReadModel student) {
            store.put(student.id(), student);
        }

        @Override
        public StudentReadModel findById(String id) {
            return store.get(id);
        }
    }
}
