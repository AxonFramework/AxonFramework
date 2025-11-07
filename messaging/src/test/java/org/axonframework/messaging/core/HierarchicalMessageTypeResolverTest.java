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

package org.axonframework.messaging.core;

import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HierarchicalMessageTypeResolverTest {

    @Nested
    class Resolve {

        @Test
        void shouldUseFallbackWhenDelegateDoNotResolve() {
            // given
            var expectedType = new MessageType("test.type", "1.0.0");
            MessageTypeResolver delegate = (pt) -> Optional.empty();
            MessageTypeResolver fallback = (pt) -> Optional.of(expectedType);
            HierarchicalMessageTypeResolver resolver = new HierarchicalMessageTypeResolver(delegate, fallback);

            // when
            var result = resolver.resolve(String.class);

            // then
            assertTrue(result.isPresent());
            assertEquals(expectedType, result.get());
        }

        @Test
        void shouldUseDelegateWhenItSucceeds() {
            // given
            var expectedType = new MessageType("test.type", "1.0.0");
            MessageTypeResolver delegate = (pt) -> Optional.of(expectedType);
            MessageTypeResolver fallback = (pt) -> Optional.empty();
            var resolver = new HierarchicalMessageTypeResolver(delegate, fallback);

            // when
            var result = resolver.resolve(String.class);

            // then
            assertTrue(result.isPresent());
            assertEquals(expectedType, result.get());
        }
    }

    @Nested
    class ResolveOrThrow {

        @Test
        void shouldNotThrowIfFallbackResolves() {
            // given
            var expectedType = new MessageType("test.type", "1.0.0");
            MessageTypeResolver delegate = (pt) -> Optional.empty();
            MessageTypeResolver fallback = (pt) -> Optional.of(expectedType);
            HierarchicalMessageTypeResolver resolver = new HierarchicalMessageTypeResolver(delegate, fallback);

            // when
            var result = assertDoesNotThrow(() -> resolver.resolveOrThrow(String.class));

            // then
            assertEquals(expectedType, result);
        }

        @Test
        void shouldThrowIfFallbackDoNotResolve() {
            // given
            MessageTypeResolver delegate = (pt) -> Optional.empty();
            MessageTypeResolver fallback = (pt) -> Optional.empty();
            HierarchicalMessageTypeResolver resolver = new HierarchicalMessageTypeResolver(delegate, fallback);

            // when/then
            assertThrows(MessageTypeNotResolvedException.class, () -> resolver.resolveOrThrow(String.class));
        }
    }
}