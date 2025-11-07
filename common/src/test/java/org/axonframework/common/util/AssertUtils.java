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

package org.axonframework.common.util;

import jakarta.annotation.Nonnull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class for special assertions.
 */
public abstract class AssertUtils {

    private AssertUtils() {
        // Utility class
    }

    /**
     * Assert that the given {@code assertion} succeeds with the given {@code time} and {@code unit}.
     *
     * @param time      an {@code int} which paired with the {@code unit} specifies the time in which the assertion must
     *                  pass
     * @param unit      a {@link TimeUnit} in which {@code time} is expressed
     * @param assertion a {@link Runnable} containing the assertion to succeed within the deadline
     */
    @SuppressWarnings("Duplicates")
    public static void assertWithin(int time, TimeUnit unit, Runnable assertion) {
        long now = System.currentTimeMillis();
        long deadline = now + unit.toMillis(time);
        do {
            try {
                assertion.run();
                break;
            } catch (AssertionError e) {
                if (now >= deadline) {
                    throw e;
                }
            }
            now = System.currentTimeMillis();
        } while (true);
    }

    /**
     * Assertion utility using {@link org.awaitility.Awaitility}, awaiting the successful completion of the given
     * {@code future} for 500ms at poll intervals of 25ms.
     *
     * @param future The completable future to await with {@link org.awaitility.Awaitility}.
     * @param <R>    The result of the given {@code future}.
     * @return The result from the given {@code future}.
     */
    public static <R> R awaitSuccessfulCompletion(@Nonnull CompletableFuture<R> future) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertTrue(
                       future.isDone() && !future.isCompletedExceptionally(),
                       () -> future.exceptionNow().toString()
               ));
        return future.join();
    }

    /**
     * Assertion utility using {@link org.awaitility.Awaitility}, awaiting the exceptional completion  of the given
     * {@code future} for 500ms at poll intervals of 25ms.
     *
     * @param future The completable future to await with {@link org.awaitility.Awaitility}.
     * @param <R>    The result of the given {@code future}.
     * @return The result from the given {@code future}.
     */
    public static <R> R awaitExceptionalCompletion(@Nonnull CompletableFuture<R> future) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertTrue(
                       future.isDone() && future.isCompletedExceptionally(),
                       "Expected exception but none occurred"
               ));
        return future.join();
    }
}