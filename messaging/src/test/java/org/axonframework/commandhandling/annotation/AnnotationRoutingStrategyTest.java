/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationRoutingStrategy}.
 *
 * @author Allard Buijze
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
    void resolvesRoutingKeyFromAnnotationDoesNotInvokeFallbackStrategy() {
        AnnotationRoutingStrategy testSubjectWithMockedFallbackStrategy =
                new AnnotationRoutingStrategy();

        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeFieldAnnotatedCommand.class), new SomeFieldAnnotatedCommand()
        );

        assertEquals("Target", testSubjectWithMockedFallbackStrategy.getRoutingKey(testCommand));
    }

    @Test
    void resolvesRoutingKeyFromFallbackStrategy() {
        AnnotationRoutingStrategy testSubjectWithMockedFallbackStrategy =
                new AnnotationRoutingStrategy();

        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType(SomeCommandWithoutTheRoutingAnnotation.class),
                new SomeCommandWithoutTheRoutingAnnotation("target")
        );

        assertNull(testSubjectWithMockedFallbackStrategy.getRoutingKey(testCommand));
    }

    public static class SomeFieldAnnotatedCommand {

        @SuppressWarnings("unused")
        @RoutingKey
        private final String target = "Target";
    }

    public static class SomeOtherFieldAnnotatedCommand {

        @SuppressWarnings("unused")
        @RoutingKey
        private final SomeObject target = new SomeObject("Target");
    }

    public static class SomeMethodAnnotatedCommand {

        @SuppressWarnings("FieldCanBeLocal")
        private final String target = "Target";

        @RoutingKey
        public String getTarget() {
            return target;
        }
    }

    public static class SomeOtherMethodAnnotatedCommand {

        private final SomeObject target = new SomeObject("Target");

        @RoutingKey
        public SomeObject getTarget() {
            return target;
        }
    }

    private record SomeObject(String target) {

        @Override
        public String toString() {
            return target;
        }
    }

    public static class SomeNullMethodAnnotatedCommand {

        private final String target = null;

        @RoutingKey
        public String getTarget() {
            //noinspection ConstantConditions
            return target;
        }
    }

    public record SomeCommandWithoutTheRoutingAnnotation(String target) {

    }
}
