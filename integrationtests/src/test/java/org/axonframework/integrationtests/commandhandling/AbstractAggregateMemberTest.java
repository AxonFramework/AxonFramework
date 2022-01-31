package org.axonframework.integrationtests.commandhandling;


import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.Before;
import org.junit.Test;

public class AbstractAggregateMemberTest {
    private FixtureConfiguration<FactoryAggregate> fixture;
    private String factoryId = "factoryId";
    @Before
    public void setUp(){
        fixture = new AggregateTestFixture(FactoryAggregate.class);
    }

    @Test
    public void testInitFactoryAggregate_ShouldHandleCommandNormally(){
        fixture.givenNoPriorActivity()
                .when(new CreateFactoryCommand(factoryId))
                .expectEvents(new FactoryCreatedEvent(factoryId));
    }

    @Test
    public void testEmployees_ShouldBeAbleToHandleCommand(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new SomethingCommand(factoryId, "employeeId"))
                .expectEvents(new SomethingEvent(factoryId, "employeeId"));
    }

    @Test
    public void testManagers_ShouldBeAbleToHandleCommand(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new SomethingCommand(factoryId, "managerId"))
                .expectEvents(new SomethingEvent(factoryId, "managerId"));
    }
}
