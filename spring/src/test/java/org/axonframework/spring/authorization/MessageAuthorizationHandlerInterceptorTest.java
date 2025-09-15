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

package org.axonframework.spring.authorization;

import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.axonframework.test.matchers.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.hamcrest.core.StringStartsWith.startsWith;

/**
 * Test class validating the {@link MessageAuthorizationHandlerInterceptor}.
 *
 * @author Roald Bankras
 */
@ExtendWith(SpringExtension.class)
class MessageAuthorizationHandlerInterceptorTest {

    private Converter converter;

    private FixtureConfiguration<TestAggregate> fixture;

    @BeforeEach
    public void setUp() {
        converter = JacksonSerializer.defaultSerializer().getConverter();

        fixture = new AggregateTestFixture<>(TestAggregate.class);
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    @WithMockUser(username = "admin", authorities = {"ROLE_aggregate.create"})
    public void shouldAuthorizeAndPropagateUsername() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new MessageAuthorizationDispatchInterceptor<>(converter))
               .registerCommandHandlerInterceptor(new MessageAuthorizationHandlerInterceptor<>())
               .registerCommandHandlerInterceptor(new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider(
                       "username")))
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectSuccessfulHandlerExecution()
               .expectResultMessageMatching(Matchers.matches(
                       message -> message.metadata().get("username").equals("admin")
               ));
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    @WithMockUser(username = "user", roles = {""})
    public void shouldNotAuthorize() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new MessageAuthorizationDispatchInterceptor<>(converter))
               .registerCommandHandlerInterceptor(new MessageAuthorizationHandlerInterceptor<>())
               .registerCommandHandlerInterceptor(new CorrelationDataInterceptor<>(
                       new SimpleCorrelationDataProvider("username")
               ))
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectException(UnauthorizedMessageException.class)
               .expectExceptionMessage(startsWith("Unauthorized message "));
    }
}
