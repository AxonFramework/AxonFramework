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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.hamcrest.core.StringStartsWith.startsWith;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AuthorizationMessageDispatchInterceptor.class, MessageAuthorizationInterceptor.class})
class AuthorizationMessageDispatchInterceptorTest {

    private FixtureConfiguration<TestAggregate> fixture;

    @BeforeEach
    public void setUp() {
        fixture = new AggregateTestFixture<>(TestAggregate.class);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_aggregate.create"})
    public void shouldAuthorizeAndPropagateUsername() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new AuthorizationMessageDispatchInterceptor<>())
               .registerCommandHandlerInterceptor(new MessageAuthorizationInterceptor<>())
               .registerCommandHandlerInterceptor(new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider(
                       "username")))
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectSuccessfulHandlerExecution()
               .expectResultMessageMatching(Matchers.matches(message -> ((User) message.getMetaData()
                                                                                       .get("username")).getUsername()
                                                                                                        .equals("admin")));
    }

    @Test
    public void shouldNotAuthorizeOnNoAuthentication() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new AuthorizationMessageDispatchInterceptor<>())
               .registerCommandHandlerInterceptor(new MessageAuthorizationInterceptor<>())
               .registerCommandHandlerInterceptor(new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider(
                       "username")))
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectExceptionMessage("No authorities found");
    }

    @Test
    @WithMockUser(username = "user", roles = {""})
    public void shouldNotAuthorizeWhenRolesMismatch() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new AuthorizationMessageDispatchInterceptor<>())
               .registerCommandHandlerInterceptor(new MessageAuthorizationInterceptor<>())
               .registerCommandHandlerInterceptor(new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider(
                       "username")))
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectException(UnauthorizedMessageException.class)
               .expectExceptionMessage(startsWith("Unauthorized message "));
    }
}
