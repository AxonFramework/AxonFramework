package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AnnotationRoutingStrategyTest {

    private AnnotationRoutingStrategy testSubject;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new AnnotationRoutingStrategy();
    }

    @Test
    public void testGetRoutingKey() throws Exception {
        String actual = testSubject.getRoutingKey(new GenericCommandMessage<Object>(new StubCommand("SomeIdentifier")));
        assertEquals("SomeIdentifier", actual);
    }

    @Test(expected = CommandDispatchException.class)
    public void testGetRoutingKey_NullKey() throws Exception {
        assertNull(testSubject.getRoutingKey(new GenericCommandMessage<Object>(new StubCommand(null))));
    }

    @Test(expected = CommandDispatchException.class)
    public void testGetRoutingKey_NoKey() throws Exception {
        assertNull(testSubject.getRoutingKey(new GenericCommandMessage<Object>("Just a String")));
    }

    @Test
    public void testGetRoutingKey_NullValueWithStaticPolicy() throws Exception {
        testSubject = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.STATIC_KEY);
        CommandMessage<Object> command = new GenericCommandMessage<Object>(new Object());
        // two calls should provide the same result
        assertEquals(testSubject.getRoutingKey(command), testSubject.getRoutingKey(command));
    }

    @Test
    public void testGetRoutingKey_NullValueWithRandomPolicy() throws Exception {
        testSubject = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY);
        CommandMessage<Object> command = new GenericCommandMessage<Object>(new Object());
        // two calls should provide the same result
        assertFalse(testSubject.getRoutingKey(command).equals(testSubject.getRoutingKey(command)));
    }

    public static class StubCommand {

        @TargetAggregateIdentifier
        private String identifier;

        public StubCommand(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }
}
