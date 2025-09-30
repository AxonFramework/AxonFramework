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

package org.axonframework.test;

import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.fixture.sampledomain.GetStudentNameQuery;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AxonTestFixtureQueryHandlerTest {

    @Test
    void givenEventsThenAwaitCommands_Success() {
        var fixture = AxonTestFixture.with(simpleQueryHandlerConfigurer());

        fixture.when()
               .query(new GetStudentNameQuery("Sample"), GetStudentNameQuery.Result.class)
               .then()
               .resultMessagePayload(new GetStudentNameQuery.Result("name-1"));
    }

    @Test
    void queryMany_ReturnsMultipleResults() {
        var configurer = MessagingConfigurer.create();
        registerQueryManyHandler(configurer);

        var fixture = AxonTestFixture.with(configurer);

        List<GetStudentNameQuery.Result> expectedResults = Arrays.asList(
                new GetStudentNameQuery.Result("name-1"),
                new GetStudentNameQuery.Result("name-2"),
                new GetStudentNameQuery.Result("name-3")
        );

        fixture.when()
               .queryMany(new GetAllStudentsQuery(), GetStudentNameQuery.Result.class)
               .then()
               .success()
               .resultCount(3)
               .results(expectedResults);
    }

    @Test
    void queryMany_WithCustomAssertion() {
        var configurer = MessagingConfigurer.create();
        registerQueryManyHandler(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .queryMany(new GetAllStudentsQuery(), GetStudentNameQuery.Result.class)
               .then()
               .success()
               .resultSatisfies(results -> {
                   assert results.size() == 3;
                   assert ((GetStudentNameQuery.Result) results.get(0)).name().equals("name-1");
                   assert ((GetStudentNameQuery.Result) results.get(1)).name().equals("name-2");
                   assert ((GetStudentNameQuery.Result) results.get(2)).name().equals("name-3");
               });
    }

    private static void registerQueryManyHandler(MessagingConfigurer configurer) {
        var queryHandling = QueryHandlingModule
                .named("test-query-many-handler")
                .queryHandlers()
                .queryHandler(
                        new QualifiedName(GetAllStudentsQuery.class),
                        new QualifiedName(GetStudentNameQuery.Result.class),
                        (q, ctx) -> MessageStream.fromIterable(Arrays.asList(
                                new GenericQueryResponseMessage(
                                        new MessageType(new QualifiedName(GetStudentNameQuery.Result.class)),
                                        new GetStudentNameQuery.Result("name-1")),
                                new GenericQueryResponseMessage(
                                        new MessageType(new QualifiedName(GetStudentNameQuery.Result.class)),
                                        new GetStudentNameQuery.Result("name-2")),
                                new GenericQueryResponseMessage(
                                        new MessageType(new QualifiedName(GetStudentNameQuery.Result.class)),
                                        new GetStudentNameQuery.Result("name-3"))
                        ))
                )
                .build();
        configurer.registerQueryHandlingModule(queryHandling);
    }

    // Simple query class for testing queryMany
    public static record GetAllStudentsQuery() {
    }

    private static ApplicationConfigurer simpleQueryHandlerConfigurer() {
        var configurer = MessagingConfigurer.create();
        var queryHandling = QueryHandlingModule
                .named("test-query-handler")
                .queryHandlers()
                .queryHandler(new QualifiedName(GetStudentNameQuery.class),
                              new QualifiedName(GetStudentNameQuery.Result.class),
                              (q, ctx) -> MessageStream.just(
                                      new GenericQueryResponseMessage(
                                              new MessageType(new QualifiedName(
                                                      GetStudentNameQuery.Result.class)),
                                              new GetStudentNameQuery.Result("name-1"))
                              )
                )
                .build();
        configurer.registerQueryHandlingModule(queryHandling);
        return configurer;
    }
}
