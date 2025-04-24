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
 * Test class validating the {@link LambdaBasedMessageTypeResolver}.
 *
 * @author Mateusz Nowak
 */
class LambdaBasedMessageTypeResolverTest {

    @Test
    void shouldThrowExceptionWhenResolvingWithNoRegisteredResolversAndNoDefaultResolver() {
        // given
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver();

        // when/then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(String.class));
        assertEquals("No resolver found for payload type [java.lang.String]", exception.getMessage());
    }

    @Test
    void shouldRegisterAndUseMessageTypeResolver() {
        // given
        MessageType expectedType = new MessageType("test.domain.CustomName", "2.0.0");
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver(
                c -> c.on(String.class, cls -> expectedType)
        );

        // when
        MessageType result = resolver.resolve(String.class);

        // then
        assertEquals(expectedType, result);
    }

    @Test
    void shouldThrowExceptionWhenRegisteringSameTypeMultipleTimes() {
        // when/then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new LambdaBasedMessageTypeResolver(
                    c -> c.on(String.class, cls -> new MessageType("first"))
                          .on(String.class, cls -> new MessageType("second"))
            );
        });

        assertEquals("A resolver is already registered for payload type [java.lang.String]", exception.getMessage());
    }

    @Test
    void shouldUseDefaultResolverWhenTypeNotExplicitlyRegistered() {
        // given
        MessageType expectedType = new MessageType(Integer.class);
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver(
                c -> c.defaultResolverTo(new ClassBasedMessageTypeResolver())
        );

        // when
        MessageType result = resolver.resolve(Integer.class);

        // then
        assertEquals(expectedType.qualifiedName(), result.qualifiedName());
        assertEquals(MessageType.DEFAULT_VERSION, result.version());
    }

    @Test
    void shouldPreferExplicitResolversOverDefaultResolver() {
        // given
        MessageType expectedType = new MessageType("test.specific", "1.0.0");
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver(
                c -> c.defaultResolverTo(new ClassBasedMessageTypeResolver())
                      .on(String.class, cls -> expectedType)
        );

        // when
        MessageType result = resolver.resolve(String.class);

        // then
        assertEquals(expectedType, result);
    }

    @Test
    void shouldUseCustomizedDefaultResolver() {
        // given
        String customVersion = "3.0.0";
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver(
                c -> c.defaultResolverTo(new ClassBasedMessageTypeResolver(customVersion))
        );

        // when
        MessageType result = resolver.resolve(Long.class);

        // then
        assertEquals(new QualifiedName(Long.class), result.qualifiedName());
        assertEquals(customVersion, result.version());
    }

    @Test
    void shouldKeepMessageTypeIfPayloadIsAMessage() {
        // given
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver(
                c -> c.on(TestPayload.class, cls -> new MessageType("custom.name", "9.9.9"))
        );

        // when
        MessageType type = new MessageType("message.type", "1.0.0");
        var payload = new GenericMessage<>(type, new TestPayload());
        MessageType result = resolver.resolve(payload);

        // then
        assertEquals(type, result);
    }

    @Test
    void shouldResolvePayloadTypeWithCorrectCasting() {
        // given
        LambdaBasedMessageTypeResolver resolver = new LambdaBasedMessageTypeResolver(
                c -> c.on(TestPayload.class,
                          cls -> new MessageType("custom.domain.test-value", "1.0.0"))
        );

        // when
        MessageType result = resolver.resolve(TestPayload.class);

        // then
        assertEquals("custom.domain.test-value", result.qualifiedName().toString());
        assertEquals("1.0.0", result.version());
    }

    /**
     * Test payload class for test cases.
     */
    private static class TestPayload {
        private final String value;

        public TestPayload() {
            this("default");
        }

        public TestPayload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}