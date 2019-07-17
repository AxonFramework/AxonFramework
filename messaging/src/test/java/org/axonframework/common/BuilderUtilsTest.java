package org.axonframework.common;

import org.junit.*;

import java.util.Random;

import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.BuilderUtils.*;
import static org.junit.Assert.*;

public class BuilderUtilsTest {

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
    public void testAssertThat() {
        assertThat(0, n -> (n == 0), "Zero must be zero");

        testAssertFails(() -> assertThat(0, n -> n != 0, "Zero must be zero"),
                        "BuilderUtils.assertThat() should have failed!");
    }

    @Test
    public void testAssertNonNull() {
        BuilderUtils.assertNonNull(this, "This is not null");

        testAssertFails(() -> assertNonNull(null, "Null should be null"),
                        "BuilderUtils.assertNonNull() should have failed on value null.");
    }

    @Test
    public void testAssertPositiveInteger() {
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
    public void testAssertPositiveLong() {
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
    public void testAssertStrictPositiveInteger() {
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
    public void testAssertStrictPositiveLong() {
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
}