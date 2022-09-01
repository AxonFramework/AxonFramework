package org.axonframework.test.saga;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.jupiter.api.*;

import static org.axonframework.test.matchers.Matchers.*;

/**
 * Test class validating a {@link org.axonframework.messaging.ScopeDescriptor}, specifically an {@link
 * org.axonframework.modelling.command.AggregateScopeDescriptor}, can be resolved on Aggregate's message handling
 * functions.
 *
 * @author Steven van Beelen
 */
class FixtureTest_ScopeDescriptor {

    private FixtureConfiguration fixture;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(TestSaga.class);
    }

    @Test
    void resolvesScopeDescriptor() {
        fixture.givenNoPriorActivity()
               .whenPublishingA(new SagaStartEvent("some-identifier"))
               .expectDispatchedCommandsMatching(payloadsMatching(sequenceOf(matches(
                       command -> ScopeDescriptorCommand.class.isAssignableFrom(command.getClass()) &&
                               SagaScopeDescriptor.class.isAssignableFrom(
                                       ((ScopeDescriptorCommand) command).scopeDescriptor.getClass()
                               )
               ))));
    }

    private static class SagaStartEvent {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String identifier;

        private SagaStartEvent(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    private static class ScopeDescriptorCommand {

        private final ScopeDescriptor scopeDescriptor;

        private ScopeDescriptorCommand(ScopeDescriptor scopeDescriptor) {
            this.scopeDescriptor = scopeDescriptor;
        }
    }

    @SuppressWarnings("unused")
    public static class TestSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "identifier")
        public void on(SagaStartEvent event, ScopeDescriptor scopeDescriptor, CommandGateway commandGateway) {
            commandGateway.send(new ScopeDescriptorCommand(scopeDescriptor));
        }
    }
}
