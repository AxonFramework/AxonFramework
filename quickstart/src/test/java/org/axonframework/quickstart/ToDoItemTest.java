package org.axonframework.quickstart;

import org.axonframework.quickstart.api.CreateToDoItemCommand;
import org.axonframework.quickstart.api.MarkCompletedCommand;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.axonframework.test.Fixtures;
import org.axonframework.test.FixtureConfiguration;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jettro Coenradie
 */
public class ToDoItemTest {
    private FixtureConfiguration fixture;

    @Before
    public void setUp() throws Exception {
        fixture = Fixtures.newGivenWhenThenFixture(ToDoItem.class);
    }

    @Test
    public void testCreateToDoItem() throws Exception {
        fixture.given()
                .when(new CreateToDoItemCommand("todo1","Need to implement the aggregate"))
                .expectEvents(new ToDoItemCreatedEvent("todo1","Need to implement the aggregate"));
    }

    @Test
    public void testMarkToDoItemAsCompleted() throws Exception {
        fixture.given(new ToDoItemCreatedEvent("todo1","Need to implement the aggregate"))
                .when(new MarkCompletedCommand("todo1"))
                .expectEvents(new ToDoItemCompletedEvent("todo1"));
    }
}
