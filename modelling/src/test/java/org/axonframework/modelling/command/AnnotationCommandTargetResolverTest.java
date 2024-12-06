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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandMessage;
import org.junit.jupiter.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationCommandTargetResolver}.
 *
 * @author Allard Buijze
 */
class AnnotationCommandTargetResolverTest {

    private AnnotationCommandTargetResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = AnnotationCommandTargetResolver.builder().build();
    }

    @Test
    void resolveTarget_CommandWithoutAnnotations() {
        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.resolveTarget(asCommandMessage("That won't work")));
    }

    @Test
    void resolveTarget_WithAnnotatedMethod() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new MethodDefaultAnnotatedIdCommand(aggregateIdentifier))
        );

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
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new MethodDefaultAnnotatedCommandWithLongVersion(aggregateIdentifier, 1L))
        );

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
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new MethodDefaultAnnotatedCommandWithStringVersion(aggregateIdentifier, "1000230"))
        );

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
        CommandMessage<Object> command = asCommandMessage(new MethodDefaultAnnotatedVoidIdCommand());
        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(command));
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
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_StringIdentifier() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = 1L;
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ObjectIdentifier() {
        final Object aggregateIdentifier = new Object();
        final Object version = 1L;
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals(version, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ParsableVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = "1";
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertEquals(aggregateIdentifier.toString(), actual.getIdentifier());
        assertEquals((Long) 1L, actual.getVersion());
    }

    @Test
    void resolveTarget_WithAnnotatedFields_NonNumericVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = "abc";
        CommandMessage<Object> command = asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version));
        assertThrows(IllegalArgumentException.class, () -> testSubject.resolveTarget(command));
    }

    private record FieldAnnotatedCommand(@TargetAggregateIdentifier Object aggregateIdentifier,
                                         @TargetAggregateVersion Object version) {

    }

    @Test
    void metaAnnotationsOnMethods() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Long version = 98765432109L;

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new MethodMetaAnnotatedCommand(aggregateIdentifier, version))
        );

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

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldMetaAnnotatedCommand(aggregateIdentifier, version)));

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

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new MethodCustomAnnotatedCommand(aggregateIdentifier, version))
        );
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

        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldCustomAnnotatedCommand(aggregateIdentifier, version)));

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