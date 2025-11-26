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

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.test.fixture.sampledomain.GetStudentById;
import org.axonframework.test.fixture.sampledomain.InMemoryStudentRepository;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.axonframework.test.fixture.sampledomain.StudentNotFoundException;
import org.axonframework.test.fixture.sampledomain.StudentProjection;
import org.axonframework.test.fixture.sampledomain.StudentQueryHandler;
import org.axonframework.test.fixture.sampledomain.StudentReadModel;
import org.axonframework.test.fixture.sampledomain.StudentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for query testing functionality in AxonTestFixture.
 * This tests the auto-await mechanism that allows queries to wait for async event processors
 * to update the read model before executing the query.
 */
public class AxonTestFixtureQueryTest {

    @Test
    void givenEventsWhenQueryThenExpectResult_Success() {
        var fixture = AxonTestFixture.with(createConfig());

        var studentId = "student-123";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "John Doe", 1);
        var expectedStudent = new StudentReadModel(studentId, "John Doe");
        var expectedWrappedResult = new GetStudentById.Result(expectedStudent);

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentById(studentId))
        .then()
               .expectQueryResult(expectedWrappedResult);
    }

    @Test
    void givenEventsWhenQueryThenExpectResultSatisfies_Success() {
        var fixture = AxonTestFixture.with(createConfig());

        var studentId = "student-456";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "Jane Smith", 1);

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentById(studentId))
        .then()
               .expectQueryResultSatisfies(result -> {
                   assertThat(result).isInstanceOf(GetStudentById.Result.class);
                   GetStudentById.Result wrappedResult = (GetStudentById.Result) result;
                   assertThat(wrappedResult.student()).isNotNull();
                   assertThat(wrappedResult.student().id()).isEqualTo(studentId);
                   assertThat(wrappedResult.student().name()).isEqualTo("Jane Smith");
               });
    }

    @Test
    void givenNoEventsWhenQueryThenExpectException_Success() {
        var fixture = AxonTestFixture.with(createConfig());

        var studentId = "non-existent-student";

        fixture.given()
        .when()
               .query(new GetStudentById(studentId), java.time.Duration.ZERO)
        .then()
               .exceptionSatisfies(exception -> {
                   assertThat(exception).hasCauseInstanceOf(StudentNotFoundException.class);
               });
    }

    @Test
    void givenNoEventsWhenQueryThenExpectExceptionWithMessage_Success() {
        var fixture = AxonTestFixture.with(createConfig());

        var studentId = "student-404";

        fixture.given()
        .when()
               .query(new GetStudentById(studentId), java.time.Duration.ZERO)
        .then()
               .exceptionSatisfies(exception -> {
                   assertThat(exception).hasCauseInstanceOf(StudentNotFoundException.class);
               });
    }

    @Test
    void givenNoEventsWhenQueryThenExpectExceptionSatisfies_Success() {
        var fixture = AxonTestFixture.with(createConfig());

        var studentId = "missing-student";

        fixture.given()
        .when()
               .query(new GetStudentById(studentId), java.time.Duration.ZERO)
        .then()
               .exceptionSatisfies(exception -> {
                   assertThat(exception).hasCauseInstanceOf(StudentNotFoundException.class);
               });
    }

    @Test
    void givenEventsWhenQueryThenSuccess_Success() {
        var fixture = AxonTestFixture.with(createConfig());

        var studentId = "student-success";
        var studentNameChanged = new StudentNameChangedEvent(studentId, "Success Student", 1);

        fixture.given()
               .events(studentNameChanged)
        .when()
               .query(new GetStudentById(studentId))
        .then()
               .success();
    }

    /**
     * Creates a test configuration with annotated projector and query handler.
     * Uses a shared in-memory repository for both event handling and query handling.
     */
    private static EventSourcingConfigurer createConfig() {
        var configurer = EventSourcingConfigurer.create();

        // Configure pooled streaming event processor for the projector
        PooledStreamingEventProcessorModule projectionProcessor = EventProcessorModule
                .pooledStreaming("student-projection-processor")
                .eventHandlingComponents(
                        c -> c.autodetected(cfg -> new StudentProjection(cfg.getComponent(StudentRepository.class)))
                ).notCustomized();

        // Configure query handler
        QueryHandlingModule queryHandler = QueryHandlingModule.named("student-query-handler")
                .queryHandlers()
                .annotatedQueryHandlingComponent(cfg -> new StudentQueryHandler(cfg.getComponent(StudentRepository.class)))
                .build();

        return configurer
                .componentRegistry(cr -> cr.registerComponent(
                        StudentRepository.class,
                        cfg -> new InMemoryStudentRepository()
                ))
                .registerQueryHandlingModule(queryHandler)
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                        eventProcessing.pooledStreaming(ps -> ps.processor(projectionProcessor))
                )));
    }
}
