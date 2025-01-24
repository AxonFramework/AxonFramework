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

package org.axonframework.messaging;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ClassBasedMessageTypeResolver}.
 *
 * @author Mateusz Nowak
 */
class ClassBasedMessageTypeResolverTest {

    @Test
    void shouldUseDefaultVersionWhenCreatedWithDefaultConstructor() {
        // given
        QualifiedName expectedName = new QualifiedName(String.class);
        ClassBasedMessageTypeResolver resolver = new ClassBasedMessageTypeResolver();

        // when
        MessageType result = resolver.resolve("Test");

        // then
        assertEquals(expectedName, result.qualifiedName());
        assertEquals(MessageType.DEFAULT_VERSION, result.version());
    }

    @Test
    void shouldUseCustomVersionWhenProvidedInConstructor() {
        // given
        QualifiedName expectedName = new QualifiedName(Integer.class);
        String customVersion = "1.0.0";
        ClassBasedMessageTypeResolver resolver = new ClassBasedMessageTypeResolver(customVersion);

        // when
        MessageType result = resolver.resolve(42);

        // then
        assertEquals(expectedName, result.qualifiedName());
        assertEquals(customVersion, result.version());
    }

    @Test
    void shouldResolvePrimitiveTypeToCorrespondingWrapperClass() {
        // given
        QualifiedName expectedName = new QualifiedName(Integer.class);
        ClassBasedMessageTypeResolver resolver = new ClassBasedMessageTypeResolver();

        // when
        MessageType result = resolver.resolve(42);

        // then
        assertEquals(expectedName, result.qualifiedName());
        assertEquals(MessageType.DEFAULT_VERSION, result.version());
    }

    @Test
    void shouldResolveCustomClassWithProvidedVersion() {
        // given
        QualifiedName expectedName = new QualifiedName(TestPayload.class);
        String customVersion = "custom-rev";
        ClassBasedMessageTypeResolver resolver = new ClassBasedMessageTypeResolver(customVersion);

        // when
        MessageType result = resolver.resolve(new TestPayload());

        // then
        assertEquals(expectedName, result.qualifiedName());
        assertEquals(customVersion, result.version());
    }

    @Test
    void shouldKeepMessageTypeIfPayloadIsAMessageAndDoNotApplyResolverVersion() {
        // given
        String customVersion = "custom-rev";
        ClassBasedMessageTypeResolver resolver = new ClassBasedMessageTypeResolver(customVersion);

        // when
        MessageType type = new MessageType("TestPayload");
        var payload = new GenericMessage<>(type, new TestPayload());
        MessageType result = resolver.resolve(payload);

        // then
        assertEquals(type, result);
        assertEquals(MessageType.DEFAULT_VERSION, result.version());
    }

    private static class TestPayload {

    }
}