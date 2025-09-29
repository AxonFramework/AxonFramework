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
import org.axonframework.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.fixture.sampledomain.GetStudentNameQuery;
import org.junit.jupiter.api.*;

public class AxonTestFixtureQueryHandlerTest {

    @Test
    void givenEventsThenAwaitCommands_Success() {
        var fixture = AxonTestFixture.with(simpleQueryHandlerConfigurer());

        fixture.when()
               .query(new GetStudentNameQuery("Sample"), GetStudentNameQuery.Result.class)
               .then()
               .resultMessagePayload(new GetStudentNameQuery.Result("name-1"));
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
