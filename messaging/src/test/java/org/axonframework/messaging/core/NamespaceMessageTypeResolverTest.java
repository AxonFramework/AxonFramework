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

package org.axonframework.messaging.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static org.junit.jupiter.api.Assertions.*;

class NamespaceMessageTypeResolverTest {

    @Test
    void shouldResolveRegisteredTypeUsingFixedNamespaceType() {
        // given
        var resolver = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", "1.0.0")
                .noFallback();

        // when
        var result = resolver.resolveOrThrow(String.class);

        // then
        var expected = new MessageType("test.string", "1.0.0");
        assertEquals(expected, result);
    }

    @Test
    void shouldThrowExceptionForUnregisteredTypeWithNamespaceUnknownFail() {
        // given
        var resolver = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", "1.0.0")
                .noFallback();

        // when/then
        var exception = assertThrows(MessageTypeNotResolvedException.class,
                                     () -> resolver.resolveOrThrow(Integer.class));
        assertEquals("Cannot resolve MessageType for the payload type [java.lang.Integer]", exception.getMessage());
    }

    @Test
    void shouldUseDefaultResolverForUnregisteredTypes() {
        // given
        var defaultResolver = new ClassBasedMessageTypeResolver("2.0.0");
        var resolver = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", "1.0.0")
                .fallback(defaultResolver);

        // when
        var result = resolver.resolveOrThrow(Integer.class);

        // then
        assertEquals(new QualifiedName(Integer.class), result.qualifiedName());
        assertEquals("2.0.0", result.version());
    }

    @Test
    void shouldPreferRegisteredResolversOverDefaultResolver() {
        // given
        var defaultResolver = new ClassBasedMessageTypeResolver("2.0.0");
        var resolver = NamespaceMessageTypeResolver
                .namespace("custom")
                .message(String.class, "string", "1.0.0")
                .fallback(defaultResolver);

        // when
        var stringResult = resolver.resolveOrThrow(String.class);
        var intResult = resolver.resolveOrThrow(Integer.class);

        // then
        var expectedString = new MessageType("custom.string", "1.0.0");
        assertEquals(expectedString, stringResult);
        assertEquals(new QualifiedName(Integer.class), intResult.qualifiedName());
        assertEquals("2.0.0", intResult.version());
    }

    @Test
    void shouldAllowChainedRegistration() {
        // given
        var resolver = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", "1.0.0")
                .message(Integer.class, "integer", "2.0.0")
                .message(Double.class, "Double", "3.0.0")
                .noFallback();

        // when
        var stringResult = resolver.resolveOrThrow(String.class);
        var intResult = resolver.resolveOrThrow(Integer.class);
        var doubleResult = resolver.resolveOrThrow(Double.class);

        // then
        assertEquals("test.string", stringResult.qualifiedName().toString());
        assertEquals("1.0.0", stringResult.version());

        assertEquals("test.integer", intResult.qualifiedName().toString());
        assertEquals("2.0.0", intResult.version());

        assertEquals("test.Double", doubleResult.qualifiedName().toString());
        assertEquals("3.0.0", doubleResult.version());
    }

    @Test
    void shouldThrowExceptionWhenRegisteringSameTypeTwice() {
        // when/then
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> NamespaceMessageTypeResolver
                                             .namespace("namespace1")
                                             .message(String.class, "first", "1.0.0")
                                             .namespace("namespace2")
                                             .message(String.class, "second", "1.0.0"));

        assertEquals("A MessageType is already defined for payload type [java.lang.String]", exception.getMessage());
    }

    @Test
    void builderShouldBeMutable() {
        // given
        var builder1 = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", "1.0.0");

        var builder2 = builder1
                .namespace("test")
                .message(Integer.class, "integer", "2.0.0");

        var resolver1 = builder1.noFallback();
        var resolver2 = builder2.noFallback();

        // then - resolver1 knows both types
        assertEquals("test.string", resolver1.resolveOrThrow(String.class).qualifiedName().toString());
        assertEquals("test.integer", resolver1.resolveOrThrow(Integer.class).qualifiedName().toString());

        // then - resolver2 knows both types
        assertEquals("test.string", resolver2.resolveOrThrow(String.class).qualifiedName().toString());
        assertEquals("test.integer", resolver2.resolveOrThrow(Integer.class).qualifiedName().toString());
    }

    @Test
    void shouldRetrieveOriginalMessageTypeFromNamespace() {
        // given
        var originalType = new MessageType("test.original", "original-version");
        var message = new GenericMessage(originalType, "payload");

        // Use a different resolver for String
        var resolver = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", "1.0.0")
                .noFallback();

        // when
        MessageType result = resolver.resolveOrThrow(message);

        // then - should return the message's type, not apply resolver for String
        assertEquals(originalType, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "2.0", "major.minor.patch", "custom-version"})
    void shouldWorkWithDifferentVersionFormats(String version) {
        // given
        var resolver = NamespaceMessageTypeResolver
                .namespace("test")
                .message(String.class, "string", version)
                .noFallback();

        // when
        var result = resolver.resolveOrThrow(String.class);

        // then
        assertEquals(version, result.version());
    }
}