/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling.annotation;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.annotation.AnnotationRoutingStrategy;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.messaging.commandhandling.annotation.RoutingKey;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationRoutingStrategy}.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 */
class AnnotationRoutingStrategyTest {

    private AnnotationRoutingStrategy testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationRoutingStrategy();
    }

    @Test
    void getRoutingKeyFromField() {
        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeFieldAnnotatedCommand.class), new SomeFieldAnnotatedCommand()
        );
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage otherTestCommand = new GenericCommandMessage(
                new MessageType(SomeOtherFieldAnnotatedCommand.class), new SomeOtherFieldAnnotatedCommand()
        );
        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void getRoutingKeyFromMethod() {
        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeMethodAnnotatedCommand.class), new SomeMethodAnnotatedCommand()
        );
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage otherTestCommand = new GenericCommandMessage(
                new MessageType(SomeOtherMethodAnnotatedCommand.class), new SomeOtherMethodAnnotatedCommand()
        );

        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void resolvesRoutingKeyFromFallbackStrategy() {
        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeCommandWithoutTheRoutingAnnotation.class),
                new SomeCommandWithoutTheRoutingAnnotation("target")
        );

        assertNull(testSubject.getRoutingKey(testCommand));
    }


    @Test
    void getRoutingKeyFromFieldLegacy() {
        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeFieldAnnotatedCommandLegacy.class), new SomeFieldAnnotatedCommandLegacy()
        );
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage otherTestCommand = new GenericCommandMessage(
                new MessageType(SomeOtherFieldAnnotatedCommandLegacy.class), new SomeOtherFieldAnnotatedCommandLegacy()
        );
        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void getRoutingKeyFromMethodLegacy() {
        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeMethodAnnotatedCommandLegacy.class), new SomeMethodAnnotatedCommandLegacy()
        );
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage otherTestCommand = new GenericCommandMessage(
                new MessageType(SomeOtherMethodAnnotatedCommandLegacy.class), new SomeOtherMethodAnnotatedCommandLegacy()
        );

        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }


    @Command(routingKey = "target")
    public static class SomeFieldAnnotatedCommand {

        @SuppressWarnings("unused")
        private final String target = "Target";
    }

    @Command
    public static class SomeFieldAnnotatedCommandLegacy {

        @RoutingKey
        @SuppressWarnings("unused")
        private final String target = "Target";
    }

    @Command(routingKey = "target")
    public static class SomeOtherFieldAnnotatedCommand {

        @SuppressWarnings("unused")
        private final SomeObject target = new SomeObject("Target");
    }

    public static class SomeOtherFieldAnnotatedCommandLegacy {

        @RoutingKey
        @SuppressWarnings("unused")
        private final SomeObject target = new SomeObject("Target");
    }

    @Command(routingKey = "target")
    public static class SomeMethodAnnotatedCommand {

        @SuppressWarnings("FieldCanBeLocal")
        private final String someObject = "Target";

        public String getTarget() {
            return someObject;
        }
    }

    @Command
    public static class SomeMethodAnnotatedCommandLegacy {

        @SuppressWarnings("FieldCanBeLocal")
        private final String target = "Target";

        @RoutingKey
        public String getTarget() {
            return target;
        }
    }

    @Command(routingKey = "target")
    public static class SomeOtherMethodAnnotatedCommand {

        private final SomeObject someObject = new SomeObject("Target");

        public SomeObject getTarget() {
            return someObject;
        }
    }

    public static class SomeOtherMethodAnnotatedCommandLegacy {

        private final SomeObject someObject = new SomeObject("Target");

        @RoutingKey
        public SomeObject getTarget() {
            return someObject;
        }
    }

    private record SomeObject(String target) {

        @Override
        public String toString() {
            return target;
        }
    }

    @Command(routingKey = "target")
    public static class SomeNullMethodAnnotatedCommand {

        private final String someObject = null;

        public String getTarget() {
            //noinspection ConstantConditions
            return someObject;
        }
    }

    public record SomeCommandWithoutTheRoutingAnnotation(String target) {

    }
}
