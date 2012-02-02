package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AnnotationRoutingKeyExtractorTest {

    private AnnotationRoutingKeyExtractor testSubject;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new AnnotationRoutingKeyExtractor();
    }

    @Test
    public void testGetRoutingKey() throws Exception {
        String actual = testSubject.getRoutingKey(new GenericCommandMessage<Object>(new StubCommand("SomeIdentifier")));
        assertEquals("SomeIdentifier", actual);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetRoutingKey_NullKey() throws Exception {
        testSubject.getRoutingKey(new GenericCommandMessage<Object>(new StubCommand(null)));
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
