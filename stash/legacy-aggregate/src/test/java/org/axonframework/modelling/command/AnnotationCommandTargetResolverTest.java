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

package org.axonframework.modelling.command;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationCommandTargetResolver}.
 *
 * @author Allard Buijze
 */
class AnnotationCommandTargetResolverTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");

    private AnnotationCommandTargetResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = AnnotationCommandTargetResolver.builder().build();
    }

    @Test
    void resolveTarget_CommandWithoutAnnotations() {
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, "That won't work");

        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(testCommand));
    }

    @Test
    void resolveTarget_WithAnnotatedMethod() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand = new GenericCommandMessage(
                TEST_COMMAND_TYPE, new MethodDefaultAnnotatedIdCommand(aggregateIdentifier)
        );

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    @SuppressWarnings({"ClassCanBeRecord", "unused"})
    private static class MethodDefaultAnnotatedIdCommand {

        private final UUID aggregateIdentifier;

        private MethodDefaultAnnotatedIdCommand(UUID aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        @TargetAggregateIdentifier
        public UUID getIdentifier() {
            return aggregateIdentifier;
        }
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new MethodDefaultAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    private record MethodDefaultAnnotatedCommand(UUID aggregateIdentifier) {

        @SuppressWarnings("unused")
        @TargetAggregateIdentifier
        private UUID getIdentifier() {
            return aggregateIdentifier;
        }
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndVoidIdentifier() {
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new MethodDefaultAnnotatedVoidIdCommand());

        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(testCommand));
    }

    private static class MethodDefaultAnnotatedVoidIdCommand {

        private MethodDefaultAnnotatedVoidIdCommand() {
        }

        @SuppressWarnings("unused")
        @TargetAggregateIdentifier
        public void getIdentifier() {
        }
    }

    @Test
    void resolveTarget_WithAnnotatedField() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    @Test
    void resolveTarget_WithAnnotatedFields_StringIdentifier() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ObjectIdentifier() {
        final Object aggregateIdentifier = new Object();
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    private record FieldAnnotatedCommand(@TargetAggregateIdentifier Object aggregateIdentifier) {

    }

    @Test
    void metaAnnotationsOnMethods() {
        final UUID aggregateIdentifier = UUID.randomUUID();

        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new MethodMetaAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    private record MethodMetaAnnotatedCommand(UUID aggregateIdentifier) {

        @MetaTargetAggregateIdentifier
        public UUID getIdentifier() {
            return aggregateIdentifier;
        }
    }

    @Test
    void metaAnnotationsOnField() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand = new GenericCommandMessage(
                TEST_COMMAND_TYPE, new FieldMetaAnnotatedCommand(aggregateIdentifier)
        );

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    @Test
    void customAnnotationsOnMethods() {
        testSubject = AnnotationCommandTargetResolver.builder()
                                                     .targetAggregateIdentifierAnnotation(
                                                             CustomTargetAggregateIdentifier.class
                                                     )
                                                     .build();

        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new MethodCustomAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);
        assertEquals(aggregateIdentifier.toString(), actual);
    }

    private record MethodCustomAnnotatedCommand(UUID aggregateIdentifier) {

        @CustomTargetAggregateIdentifier
        public UUID getIdentifier() {
            return aggregateIdentifier;
        }
    }

    @Test
    void customAnnotationsOnField() {
        testSubject = AnnotationCommandTargetResolver.builder()
                                                     .targetAggregateIdentifierAnnotation(
                                                             CustomTargetAggregateIdentifier.class
                                                     )
                                                     .build();

        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage testCommand =
                new GenericCommandMessage(TEST_COMMAND_TYPE, new FieldCustomAnnotatedCommand(aggregateIdentifier));

        String actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual);
    }

    private record FieldMetaAnnotatedCommand(@MetaTargetAggregateIdentifier Object aggregateIdentifier) {

    }

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @TargetAggregateIdentifier @interface MetaTargetAggregateIdentifier {

    }

    private record FieldCustomAnnotatedCommand(@CustomTargetAggregateIdentifier Object aggregateIdentifier) {

    }

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME) @interface CustomTargetAggregateIdentifier {

    }
}