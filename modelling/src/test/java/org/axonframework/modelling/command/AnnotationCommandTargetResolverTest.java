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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MessageType;
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
        CommandMessage<String> testCommand =
                new GenericCommandMessage<>(TEST_COMMAND_TYPE, "That won't work");

        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(testCommand));
    }

    @Test
    void resolveTarget_WithAnnotatedMethod() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage<MethodDefaultAnnotatedIdCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new MethodDefaultAnnotatedIdCommand(aggregateIdentifier)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertNull(actual.getVersion());
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
        CommandMessage<MethodDefaultAnnotatedCommandWithLongVersion> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE,
                new MethodDefaultAnnotatedCommandWithLongVersion(aggregateIdentifier, 1L)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals((Long) 1L, actual.getVersion());
    }

    @SuppressWarnings({"ClassCanBeRecord", "unused"})
    private static class MethodDefaultAnnotatedCommandWithLongVersion {

        private final UUID aggregateIdentifier;
        private final Long version;

        private MethodDefaultAnnotatedCommandWithLongVersion(UUID aggregateIdentifier, Long version) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.version = version;
        }

        @TargetAggregateIdentifier
        private UUID getIdentifier() {
            return aggregateIdentifier;
        }

        @TargetAggregateVersion
        private Long version() {
            return version;
        }
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndStringVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        CommandMessage<MethodDefaultAnnotatedCommandWithStringVersion> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE,
                new MethodDefaultAnnotatedCommandWithStringVersion(aggregateIdentifier, "1000230")
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals((Long) 1000230L, actual.getVersion());
    }

    @SuppressWarnings({"ClassCanBeRecord", "unused"})
    private static class MethodDefaultAnnotatedCommandWithStringVersion {

        private final UUID aggregateIdentifier;
        private final String version;

        private MethodDefaultAnnotatedCommandWithStringVersion(UUID aggregateIdentifier, String version) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.version = version;
        }

        @TargetAggregateIdentifier
        private UUID getIdentifier() {
            return aggregateIdentifier;
        }

        @TargetAggregateVersion
        private String version() {
            return version;
        }
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndVoidIdentifier() {
        CommandMessage<Object> testCommand =
                new GenericCommandMessage<>(TEST_COMMAND_TYPE, new MethodDefaultAnnotatedVoidIdCommand());

        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(testCommand));
    }

    @SuppressWarnings("unused")
    private static class MethodDefaultAnnotatedVoidIdCommand {

        private MethodDefaultAnnotatedVoidIdCommand() {
        }

        @TargetAggregateIdentifier
        public void getIdentifier() {
        }
    }

    @Test
    void resolveTarget_WithAnnotatedFields() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = 1L;
        CommandMessage<FieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_StringIdentifier() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = 1L;
        CommandMessage<FieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ObjectIdentifier() {
        final Object aggregateIdentifier = new Object();
        final Object version = 1L;
        CommandMessage<FieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ParsableVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = "1";
        CommandMessage<FieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals((Long) 1L, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_NonNumericVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = "abc";
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldAnnotatedCommand(aggregateIdentifier, version)
        );

        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(testCommand));
    }

    private record FieldAnnotatedCommand(@TargetAggregateIdentifier Object aggregateIdentifier,
                                         @TargetAggregateVersion Object version) {

    }

    @Test
    void metaAnnotationsOnMethods() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Long version = 98765432109L;

        CommandMessage<MethodMetaAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new MethodMetaAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @SuppressWarnings({"ClassCanBeRecord", "unused"})
    private static class MethodMetaAnnotatedCommand {

        private final UUID aggregateIdentifier;
        private final Long version;

        private MethodMetaAnnotatedCommand(UUID aggregateIdentifier, Long version) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.version = version;
        }

        @MetaTargetAggregateIdentifier
        public UUID getIdentifier() {
            return aggregateIdentifier;
        }

        @MetaTargetAggregateVersion
        public Long version() {
            return version;
        }
    }

    @Test
    void metaAnnotationsOnFields() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Long version = 98765432109L;
        CommandMessage<FieldMetaAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldMetaAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void customAnnotationsOnMethods() {
        testSubject = AnnotationCommandTargetResolver.builder()
                                                     .targetAggregateIdentifierAnnotation(
                                                             CustomTargetAggregateIdentifier.class
                                                     )
                                                     .targetAggregateVersionAnnotation(
                                                             CustomTargetAggregateVersion.class
                                                     )
                                                     .build();

        final UUID aggregateIdentifier = UUID.randomUUID();
        final Long version = 98765432109L;
        CommandMessage<MethodCustomAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new MethodCustomAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);
        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @SuppressWarnings({"ClassCanBeRecord", "unused"})
    private static class MethodCustomAnnotatedCommand {

        private final UUID aggregateIdentifier;
        private final Long version;

        private MethodCustomAnnotatedCommand(UUID aggregateIdentifier, Long version) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.version = version;
        }

        @CustomTargetAggregateIdentifier
        public UUID getIdentifier() {
            return aggregateIdentifier;
        }

        @CustomTargetAggregateVersion
        public Long version() {
            return version;
        }
    }

    @Test
    void customAnnotationsOnFields() {
        testSubject = AnnotationCommandTargetResolver.builder()
                                                     .targetAggregateIdentifierAnnotation(
                                                             CustomTargetAggregateIdentifier.class
                                                     )
                                                     .targetAggregateVersionAnnotation(
                                                             CustomTargetAggregateVersion.class
                                                     )
                                                     .build();

        final UUID aggregateIdentifier = UUID.randomUUID();
        final Long version = 98765432109L;
        CommandMessage<FieldCustomAnnotatedCommand> testCommand = new GenericCommandMessage<>(
                TEST_COMMAND_TYPE, new FieldCustomAnnotatedCommand(aggregateIdentifier, version)
        );

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(testCommand);

        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    private record FieldMetaAnnotatedCommand(@MetaTargetAggregateIdentifier Object aggregateIdentifier,
                                             @MetaTargetAggregateVersion Object version) {

    }

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @TargetAggregateIdentifier @interface MetaTargetAggregateIdentifier {

    }

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @TargetAggregateVersion @interface MetaTargetAggregateVersion {

    }

    private record FieldCustomAnnotatedCommand(@CustomTargetAggregateIdentifier Object aggregateIdentifier,
                                               @CustomTargetAggregateVersion Object version) {

    }

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME) @interface CustomTargetAggregateIdentifier {

    }

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME) @interface CustomTargetAggregateVersion {

    }
}