/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.RoutingKey;
import org.junit.jupiter.api.*;

import static org.axonframework.messaging.QualifiedName.className;
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
        CommandMessage<SomeFieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                className(SomeFieldAnnotatedCommand.class), new SomeFieldAnnotatedCommand()
        );
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage<SomeOtherFieldAnnotatedCommand> otherTestCommand = new GenericCommandMessage<>(
                className(SomeOtherFieldAnnotatedCommand.class), new SomeOtherFieldAnnotatedCommand()
        );
        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void getRoutingKeyFromMethod() {
        CommandMessage<SomeMethodAnnotatedCommand> testCommand = new GenericCommandMessage<>(className(
                SomeMethodAnnotatedCommand.class), new SomeMethodAnnotatedCommand()
        );
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage<SomeOtherMethodAnnotatedCommand> otherTestCommand = new GenericCommandMessage<>(
                className(SomeOtherMethodAnnotatedCommand.class), new SomeOtherMethodAnnotatedCommand()
        );

        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void resolvesRoutingKeyFromAnnotationDoesNotInvokeFallbackStrategy() {
        AnnotationRoutingStrategy testSubjectWithMockedFallbackStrategy =
                new AnnotationRoutingStrategy();

        CommandMessage<SomeFieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                className(SomeFieldAnnotatedCommand.class), new SomeFieldAnnotatedCommand()
        );

        assertEquals("Target", testSubjectWithMockedFallbackStrategy.getRoutingKey(testCommand));
    }

    @Test
    void resolvesRoutingKeyFromFallbackStrategy() {
        AnnotationRoutingStrategy testSubjectWithMockedFallbackStrategy =
                new AnnotationRoutingStrategy();

        CommandMessage<SomeCommandWithoutTheRoutingAnnotation> testCommand = new GenericCommandMessage<>(
                className(SomeCommandWithoutTheRoutingAnnotation.class),
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
