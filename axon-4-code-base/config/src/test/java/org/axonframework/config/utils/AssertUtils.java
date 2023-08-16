/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.config.utils;

import org.opentest4j.AssertionFailedError;

import java.time.Duration;

/**
 * Utility class for special assertions
 */
public abstract class AssertUtils {

    private AssertUtils() {
        // Utility class
    }

    /**
     * Assert that the given {@code assertion} succeeds with the given {@code duration}.
     *
     * @param duration  specifies the time in which the assertion must
     *                  pass
     * @param assertion a {@link Runnable} containing the assertion to succeed within the deadline
     */
    @SuppressWarnings("Duplicates")
    public static void assertRetryingWithin(Duration duration, Runnable assertion) {
        long now = System.currentTimeMillis();
        long deadline = now + duration.toMillis();
        do {
            try {
                assertion.run();
                break;
            } catch (AssertionFailedError e) {
                if (now >= deadline) {
                    throw e;
                }
            }
            now = System.currentTimeMillis();
        } while (true);
    }
}