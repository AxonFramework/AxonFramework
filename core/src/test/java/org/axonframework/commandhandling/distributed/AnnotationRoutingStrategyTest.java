package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AnnotationRoutingStrategyTest {
    private AnnotationRoutingStrategy testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AnnotationRoutingStrategy();
    }

    @Test
    public void testGetRoutingKeyFromField() throws Exception {
        assertEquals("Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeFieldAnnotatedCommand())));
        assertEquals("Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeOtherFieldAnnotatedCommand())));
    }

    @Test
    public void testGetRoutingKeyFromMethod() throws Exception {
        assertEquals("Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeMethodAnnotatedCommand())));
        assertEquals("Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeOtherMethodAnnotatedCommand())));
    }

    public static class SomeFieldAnnotatedCommand {

        @TargetAggregateIdentifier
        private final String target = "Target";
    }

    public static class SomeOtherFieldAnnotatedCommand {

        @TargetAggregateIdentifier
        private final SomeObject target = new SomeObject("Target");

    }
    public static class SomeMethodAnnotatedCommand {

        private final String target = "Target";

        @TargetAggregateIdentifier
        public String getTarget() {
            return target;
        }
    }

    public static class SomeOtherMethodAnnotatedCommand {

        private final SomeObject target = new SomeObject("Target");

        @TargetAggregateIdentifier
        public SomeObject getTarget() {
            return target;
        }
    }


    private static class SomeObject {
        private final String target;

        public SomeObject(String target) {
            this.target = target;
        }

        @Override
        public String toString() {
            return target;
        }
    }

}
