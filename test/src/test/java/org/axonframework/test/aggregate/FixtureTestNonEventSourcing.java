package org.axonframework.test.aggregate;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FixtureTestNonEventSourcing {
    private FixtureConfiguration<NonEventSourceAggregate> fixture;

    @Before
    public void setUp() {
        fixture = new AggregateTestFixture<>(NonEventSourceAggregate.class);
        InMemoryRepository<NonEventSourceAggregate> repository = new InMemoryRepository<>(NonEventSourceAggregate.class, fixture.getEventBus());
        fixture.registerRepository(repository);
        fixture.registerAnnotatedCommandHandler(new NonEventSourceHandler(repository));
    }

    @Test
    public void testLoadAggregate() {
        String id = UUID.randomUUID().toString();
        fixture.givenCommands(new NonEventSourceHandler.CreateNonEventSourceCommand(id, "NAME"))
                .when(new NonEventSourceHandler.ModifyNameNonEventSourceCommand(id, "NEW_NAME"))
                .expectEvents(new NonEventSourceNameChanged(id, "NEW_NAME"));
    }
    @Test
    public void testHandlerInAggregate() {
        String id = UUID.randomUUID().toString();
        fixture.givenCommands(new NonEventSourceHandler.CreateNonEventSourceCommand(id, "NAME"))
                .when(new NonEventSourceHandler.ModifyNameNonEventSource2Command(id, "NEW_NAME"))
                .expectEvents(new NonEventSourceNameChanged(id, "NEW_NAME"));
    }
}
