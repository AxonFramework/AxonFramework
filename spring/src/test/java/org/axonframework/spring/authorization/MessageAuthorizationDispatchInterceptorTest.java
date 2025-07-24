/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.axonframework.test.matchers.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.hamcrest.core.StringStartsWith.startsWith;

/**
 * Test class validating the {@link MessageAuthorizationDispatchInterceptor}.
 *
 * @author Roald Bankras
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MessageAuthorizationDispatchInterceptorTest.TestContext.class)
class MessageAuthorizationDispatchInterceptorTest {

    private FixtureConfiguration<TestAggregate> fixture;

    @BeforeEach
    public void setUp() {
        fixture = new AggregateTestFixture<>(TestAggregate.class);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_aggregate.create", "ROLE_aggregate.update"})
    public void shouldAuthorizeAndPropagateAuthorities() {
        // We expect concatenated roles, separating by commas.
        String expectedAuthorities = "ROLE_aggregate.create,ROLE_aggregate.update";
        UUID aggregateId = UUID.randomUUID();

        fixture.registerCommandDispatchInterceptor(new MessageAuthorizationDispatchInterceptor<>())
               .registerCommandHandlerInterceptor(new MessageAuthorizationHandlerInterceptor<>())
               .registerCommandHandlerInterceptor(
                       new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider("authorities"))
               )
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectSuccessfulHandlerExecution()
               .expectResultMessageMatching(Matchers.matches(
                       message -> message.getMetaData().get("authorities").equals(expectedAuthorities)
               ));
    }

    @Test
    public void shouldNotAuthorizeOnNoAuthentication() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new MessageAuthorizationDispatchInterceptor<>())
               .registerCommandHandlerInterceptor(new MessageAuthorizationHandlerInterceptor<>())
               .registerCommandHandlerInterceptor(
                       new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider("authorities"))
               )
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectException(UnauthorizedMessageException.class)
               .expectExceptionMessage(startsWith("No authorities found"));
    }

    @Test
    @WithMockUser(username = "user", roles = {""})
    public void shouldNotAuthorizeWhenRolesMismatch() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new MessageAuthorizationDispatchInterceptor<>())
               .registerCommandHandlerInterceptor(new MessageAuthorizationHandlerInterceptor<>())
               .registerCommandHandlerInterceptor(
                       new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider("authorities"))
               )
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectException(UnauthorizedMessageException.class)
               .expectExceptionMessage(startsWith("Unauthorized message "));
    }

    @Configuration
    static class TestContext {

        @Bean
        public MessageAuthorizationDispatchInterceptor<?> messageAuthorizationDispatchInterceptor() {
            return new MessageAuthorizationDispatchInterceptor<>();
        }

        @Bean
        public MessageAuthorizationHandlerInterceptor<?> messageAuthorizationHandlerInterceptor() {
            return new MessageAuthorizationHandlerInterceptor<>();
        }
    }
}
