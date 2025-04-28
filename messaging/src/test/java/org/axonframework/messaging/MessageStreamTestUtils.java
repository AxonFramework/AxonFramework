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

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

public class MessageStreamTestUtils {
    private MessageStreamTestUtils() {
        // Prevent instantiation
    }

    /**
     * Asserts that the given {@code stream} completed exceptionally with the given {@code expectedExceptionType} and
     * {@code expectedMessage}.
     * @param stream The {@link MessageStream} to assert.
     * @param expectedExceptionType The expected type of the exception.
     * @param expectedMessage The expected message of the exception.
     */
    public static void assertCompletedExceptionally(
            MessageStream<?> stream,
            Class<? extends Throwable> expectedExceptionType,
            String expectedMessage
    ) {
        var exception = assertThrows(CompletionException.class, () -> {
            stream.first().asCompletableFuture().join();
        });
        assertInstanceOf(expectedExceptionType, exception.getCause());
        assertEquals(expectedMessage,
                     exception.getCause().getMessage());
    }
}
