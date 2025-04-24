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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link LambdaBasedMessageTypeResolver}
 *
 * @author Mateusz Nowak
 */
class LambdaBasedMessageTypeResolverTest {

    @Test
    void shouldResolveRegisteredType() {
        // given
        MessageType expected = new MessageType("test.string", "1.0.0");
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> expected)
                .failByDefault();

        // when
        MessageType result = resolver.resolve(String.class);

        // then
        assertEquals(expected, result);
    }

    @Test
    void shouldThrowExceptionForUnregisteredTypeWithFailByDefault() {
        // given
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> new MessageType("test.string", "1.0.0"))
                .failByDefault();

        // when/then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> resolver.resolve(Integer.class));
        assertEquals("No resolver found for payload type [java.lang.Integer]", exception.getMessage());
    }

    @Test
    void shouldUseDefaultResolverForUnregisteredTypes() {
        // given
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> new MessageType("test.string", "1.0.0"))
                .useByDefault(new ClassBasedMessageTypeResolver("2.0.0"));

        // when
        MessageType result = resolver.resolve(Integer.class);

        // then
        assertEquals(new QualifiedName(Integer.class), result.qualifiedName());
        assertEquals("2.0.0", result.version());
    }

    @Test
    void shouldPreferRegisteredResolversOverDefaultResolver() {
        // given
        MessageType expectedString = new MessageType("custom.string", "1.0.0");
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> expectedString)
                .useByDefault(new ClassBasedMessageTypeResolver("2.0.0"));

        // when
        MessageType stringResult = resolver.resolve(String.class);
        MessageType intResult = resolver.resolve(Integer.class);

        // then
        assertEquals(expectedString, stringResult);
        assertEquals(new QualifiedName(Integer.class), intResult.qualifiedName());
        assertEquals("2.0.0", intResult.version());
    }

    @Test
    void shouldAllowChainedRegistration() {
        // given
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> new MessageType("custom.string", "1.0.0"))
                .on(Integer.class, cls -> new MessageType("custom.int", "2.0.0"))
                .on(Double.class, cls -> new MessageType("custom.double", "3.0.0"))
                .failByDefault();

        // when
        MessageType stringResult = resolver.resolve(String.class);
        MessageType intResult = resolver.resolve(Integer.class);
        MessageType doubleResult = resolver.resolve(Double.class);

        // then
        assertEquals("custom.string", stringResult.qualifiedName().toString());
        assertEquals("1.0.0", stringResult.version());

        assertEquals("custom.int", intResult.qualifiedName().toString());
        assertEquals("2.0.0", intResult.version());

        assertEquals("custom.double", doubleResult.qualifiedName().toString());
        assertEquals("3.0.0", doubleResult.version());
    }

    @Test
    void shouldThrowExceptionWhenRegisteringSameTypeTwice() {
        // when/then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> LambdaBasedMessageTypeResolver
                                                                  .on(String.class, cls -> new MessageType("first"))
                                                                  .on(String.class, cls -> new MessageType("second")));

        assertEquals("A resolver is already registered for payload type [java.lang.String]", exception.getMessage());
    }

    @Test
    void shouldCreateImmutablePhaseInstances() {
        // given
        var phase1 = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> new MessageType("test.string", "1.0.0"));

        var phase2 = phase1
                .on(Integer.class, cls -> new MessageType("test.integer", "2.0.0"));

        var resolver1 = phase1.failByDefault();
        var resolver2 = phase2.failByDefault();

        // then - resolver1 doesn't know about Integer
        assertThrows(IllegalArgumentException.class,
                     () -> resolver1.resolve(Integer.class));

        // then - resolver2 knows both types
        assertEquals("test.string", resolver2.resolve(String.class).qualifiedName().toString());
        assertEquals("test.integer", resolver2.resolve(Integer.class).qualifiedName().toString());
    }

    @Test
    void shouldHandleMethodReferencesAsResolvers() {
        // given
        MessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, this::createStringType)
                .failByDefault();

        // when
        MessageType result = resolver.resolve(String.class);

        // then
        assertEquals("test.string.methodref", result.qualifiedName().toString());
        assertEquals("ref-version", result.version());
    }

    @Test
    void shouldUtilizeClassInfoInResolvers() {
        // given
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(TestEvent.class, cls -> {
                    String name = cls.getSimpleName();
                    String pkg = cls.getPackageName();
                    return new MessageType(pkg + ".event." + name, "1.0.0");
                })
                .failByDefault();

        // when
        MessageType result = resolver.resolve(TestEvent.class);

        // then
        assertEquals("org.axonframework.messaging.event.TestEvent", result.qualifiedName().toString());
        assertEquals("1.0.0", result.version());
    }

    @Test
    void shouldRetrieveOriginalMessageTypeFromMessage() {
        // given
        MessageType originalType = new MessageType("test.original", "original-version");
        GenericMessage<String> message = new GenericMessage<>(originalType, "payload");

        // Use a different resolver for String
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> new MessageType("test.string", "1.0.0"))
                .failByDefault();

        // when
        MessageType result = resolver.resolve(message);

        // then - should return the message's type, not apply resolver for String
        assertEquals(originalType, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "2.0", "major.minor.patch", "custom-version"})
    void shouldWorkWithDifferentVersionFormats(String version) {
        // given
        LambdaBasedMessageTypeResolver resolver = LambdaBasedMessageTypeResolver
                .on(String.class, cls -> new MessageType("test.string", version))
                .failByDefault();

        // when
        MessageType result = resolver.resolve(String.class);

        // then
        assertEquals(version, result.version());
    }

    private MessageType createStringType(Class<?> cls) {
        return new MessageType("test.string.methodref", "ref-version");
    }

    private static class TestEvent {
        private final String data;

        public TestEvent(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }
}