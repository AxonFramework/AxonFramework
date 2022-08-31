/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.common;

import org.junit.jupiter.api.*;

import java.util.Random;

import static org.axonframework.common.BuilderUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating all the methods in the {@link BuilderUtils}.
 *
 * @author Bert Laverman
 */
class BuilderUtilsTest {

    private static final int NUMBER_OF_RANDOM_NUMBERS = 256;

    private static void testAssertFails(Runnable r, final String failureMsg) {
        try {
            r.run();
            fail(failureMsg);
        } catch (AxonConfigurationException e) {
            //Good!
        }
    }

    @Test
    void assertThatTest() {
        assertThat(0, n -> (n == 0), "Zero must be zero");

        testAssertFails(() -> assertThat(0, n -> n != 0, "Zero must be zero"),
                        "BuilderUtils.assertThat() should have failed!");
    }

    @Test
    void assertNonNullTest() {
        assertNonNull(this, "This is not null");

        testAssertFails(() -> assertNonNull(null, "Null should be null"),
                        "BuilderUtils.assertNonNull() should have failed on value null.");
    }

    @Test
    void assertPositiveInteger() {
        // Fixed tests
        assertPositive(0, "Zero is positive");
        assertPositive(1, "One is also positive");
        testAssertFails(() -> assertPositive(-1, "Minus one is not positive"),
                        "BuilderUtils.assertPositive() should have failed on value -1.");

        // Random sample
        Random random = new Random();
        for (int i = 0; i < NUMBER_OF_RANDOM_NUMBERS; i++) {
            int value = random.nextInt();
            if (value >= 0) {
                assertPositive(value, "Value " + value + " is positive.");
            } else {
                testAssertFails(() -> assertPositive(value, "fail"), "Value " + value + " is negative.");
            }
        }
    }

    @Test
    void assertPositiveLong() {
        // Fixed tests
        assertPositive(0L, "Zero is positive");
        assertPositive(1L, "One is also positive");
        testAssertFails(() -> assertPositive(-1L, "Minus one is not positive"),
                        "BuilderUtils.assertPositive() should have failed on value -1.");

        // Random sample
        Random random = new Random();
        for (int i = 0; i < NUMBER_OF_RANDOM_NUMBERS; i++) {
            long value = random.nextLong();
            if (value >= 0L) {
                assertPositive(value, "Value " + value + " is positive.");
            } else {
                testAssertFails(() -> assertPositive(value, "fail"), "Value " + value + " is negative.");
            }
        }
    }

    @Test
    void assertStrictPositiveInteger() {
        // Fixed tests
        assertStrictPositive(1, "One is positive");
        testAssertFails(() -> assertStrictPositive(0, "Zero is not strict positive"),
                        "BuilderUtils.assertPositive() should have failed on value -1.");
        testAssertFails(() -> assertStrictPositive(-1, "Minus one is not positive"),
                        "BuilderUtils.assertPositive() should have failed on value -1.");

        // Random sample
        Random random = new Random();
        for (int i = 0; i < NUMBER_OF_RANDOM_NUMBERS; i++) {
            int value = random.nextInt();
            if (value > 0) {
                assertStrictPositive(value, "Value " + value + " is positive.");
            } else {
                testAssertFails(() -> assertStrictPositive(value, "fail"), "Value " + value + " is negative.");
            }
        }
    }

    @Test
    void assertStrictPositiveLong() {
        // Fixed tests
        assertStrictPositive(1L, "One is also positive");
        testAssertFails(() -> assertStrictPositive(0L, "Zero is not strict positive"),
                        "BuilderUtils.assertPositive() should have failed on value -1.");
        testAssertFails(() -> assertStrictPositive(-1L, "Minus one is not positive"),
                        "BuilderUtils.assertPositive() should have failed on value -1.");

        // Random sample
        Random random = new Random();
        for (int i = 0; i < NUMBER_OF_RANDOM_NUMBERS; i++) {
            long value = random.nextLong();
            if (value > 0L) {
                assertStrictPositive(value, "Value " + value + " is positive.");
            } else {
                testAssertFails(() -> assertStrictPositive(value, "fail"), "Value " + value + " is negative.");
            }
        }
    }

    @Test
    void assertNonEmptyTest() {
        assertNonEmpty("some-text", "Reacts fine on some text");
        testAssertFails(() -> assertNonEmpty(null, "Should fail on null"),
                        "BuilderUtils.assertNonEmpty() should have failed on value null.");
        testAssertFails(() -> assertNonEmpty("", "Should fail on an empty string"),
                        "BuilderUtils.assertNonEmpty() should have failed on an empty string.");
    }
}