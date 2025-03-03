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

package org.axonframework.common;

import java.util.Random;

import static org.axonframework.common.Assert.assertStrictPositive;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/**
 * Test class validating the {@code static} methods of the {@link Assert} utility.
 *
 * @author Allard Buijze
 */
class AssertTest {

    private static final int NUMBER_OF_RANDOM_NUMBERS = 256;

    @Test
    void stateAccept() {
        Assert.state(true, () -> "Hello");
    }

    @Test
    void stateFail() {
        assertThrows(IllegalStateException.class, () -> Assert.state(false, () -> "Hello"));
    }

    @Test
    void isTrueAccept() {
        Assert.isTrue(true, () -> "Hello");
    }

    @Test
    void isTrueFail() {
        assertThrows(IllegalArgumentException.class, () -> Assert.isTrue(false, () -> "Hello"));
    }

    @Test
    void nonEmpty() {
        assertDoesNotThrow(() -> Assert.nonEmpty("some-text", "Reacts fine on some text"));
        assertThrows(IllegalArgumentException.class, () -> Assert.nonEmpty(null, "Should fail on null"));
        assertThrows(IllegalArgumentException.class, () -> Assert.nonEmpty("", "Should fail on an empty string"));
    }

    @Test
    void assertStrictPositiveInteger() {
        // Fixed tests
        assertStrictPositive(1, "One is positive");
        assertThrows(IllegalArgumentException.class, () -> assertStrictPositive(0, "Zero is not strict positive"));
        assertThrows(IllegalArgumentException.class, () -> assertStrictPositive(-1, "Minus one is not positive"));

        // Random sample
        Random random = new Random();
        for (int i = 0; i < NUMBER_OF_RANDOM_NUMBERS; i++) {
            int value = random.nextInt();
            if (value > 0) {
                assertStrictPositive(value, "Value " + value + " is positive.");
            } else {
                assertThrows(IllegalArgumentException.class, () -> assertStrictPositive(value, "fail"));
            }
        }
    }

    @Test
    void assertStrictPositiveLong() {
        // Fixed tests
        assertStrictPositive(1L, "One is also positive");
        assertThrows(IllegalArgumentException.class, () -> assertStrictPositive(0L, "Zero is not strict positive"));
        assertThrows(IllegalArgumentException.class, () -> assertStrictPositive(-1L, "Minus one is not positive"));

        // Random sample
        Random random = new Random();
        for (int i = 0; i < NUMBER_OF_RANDOM_NUMBERS; i++) {
            long value = random.nextLong();
            if (value > 0L) {
                assertStrictPositive(value, "Value " + value + " is positive.");
            } else {
                assertThrows(IllegalArgumentException.class, () -> assertStrictPositive(value, "fail"));
            }
        }
    }
}
