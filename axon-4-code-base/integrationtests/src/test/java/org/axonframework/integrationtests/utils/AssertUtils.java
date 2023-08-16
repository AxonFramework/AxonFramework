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

package org.axonframework.integrationtests.utils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Utility class providing additional forms of assertions, mainly involving time based checks.
 *
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
public abstract class AssertUtils {

    private AssertUtils() {
        // Utility class
    }

    /**
     * Assert that the given {@code assertion} succeeds with the given {@code time} and {@code unit}.
     *
     * @param time      The time in which the assertion must pass
     * @param unit      The unit in which time is expressed
     * @param assertion the assertion to succeed within the deadline
     */
    public static void assertWithin(int time, TimeUnit unit, Runnable assertion) {
        long now = System.currentTimeMillis();
        long deadline = now + unit.toMillis(time);
        do {
            try {
                assertion.run();
                break;
            } catch (AssertionError e) {
                Thread.yield();
                if (now >= deadline) {
                    throw e;
                }
            }
            now = System.currentTimeMillis();
        } while (true);
    }

    /**
     * Assert that the given {@code assertion} succeeds for the entire {@code timeFrame}. The given {@code
     * validationInterval} defines the interval between consecutive validations of the {@code assertion}.
     *
     * @param timeFrame          the {@link Duration} for which the given {@code assertion} should succeed
     * @param validationInterval the {@link Duration} to wait between consecutive validation attempts of the given
     *                           {@code assertion}
     * @param assertion          a {@link Runnable} containing the assertion to succeed for the given {@code timeFrame}
     */
    public static void assertFor(Duration timeFrame, Duration validationInterval, Runnable assertion) {
        long now = System.currentTimeMillis();
        long deadline = timeFrame.plusMillis(now).toMillis();
        while (now < deadline) {
            assertion.run();
            try {
                Thread.sleep(validationInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(
                        "Testing thread has been interrupted. Unable to keep validating the given assertion."
                );
            }
            now = System.currentTimeMillis();
        }
    }

    /**
     * Assert that the given {@code assertion} succeeds, up to and {@code until} the {@link BooleanSupplier} returns
     * {@code false}. The given {@code validationInterval} defines the interval between consecutive validations of the
     * {@code assertion}.
     *
     * @param until              a {@link BooleanSupplier} to return {@code false} if the given {@code assertion} should
     *                           not be validated any more
     * @param validationInterval the {@link Duration} to wait between consecutive validation attempts of the given
     *                           {@code assertion}
     * @param assertion          a {@link Runnable} containing the assertion to succeed up to and {@code until} the
     *                           supplier returns {@code false}
     */
    public static void assertUntil(BooleanSupplier until, Duration validationInterval, Runnable assertion) {
        while (until.getAsBoolean()) {
            assertion.run();
            try {
                Thread.sleep(validationInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(
                        "Testing thread has been interrupted. Unable to keep validating the given assertion."
                );
            }
        }
    }
}
