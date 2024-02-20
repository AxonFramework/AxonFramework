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

/**
 * Command Authorization Interceptor test
 *
 * @author Roald Bankras
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AuthorizationMessageDispatchInterceptor.class, CommandAuthorizationInterceptor.class})
class CommandAuthorizationInterceptorTest {

    private FixtureConfiguration<TestAggregate> fixture;

    @BeforeEach
    public void setUp() {
        fixture = new AggregateTestFixture<>(TestAggregate.class);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"aggregate.create"})
    public void shouldAuthorizeAndPropagateUsername() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new AuthorizationMessageDispatchInterceptor())
               .registerCommandHandlerInterceptor(new CommandAuthorizationInterceptor())
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
    @WithMockUser(username = "user", roles = {""})
    public void shouldNotAuthorize() {
        UUID aggregateId = UUID.randomUUID();
        fixture.registerCommandDispatchInterceptor(new AuthorizationMessageDispatchInterceptor())
               .registerCommandHandlerInterceptor(new CommandAuthorizationInterceptor())
               .registerCommandHandlerInterceptor(new CorrelationDataInterceptor<>(new SimpleCorrelationDataProvider(
                       "username")))
               .given()
               .when(new CreateAggregateCommand(aggregateId))
               .expectExceptionMessage("Unauthorized command");
    }
}
