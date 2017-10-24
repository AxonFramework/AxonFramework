package org.axonframework.common;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for special assertions
 */
public class AssertUtils {

    private AssertUtils() {

    }

    /**
     * Assert that the given {@code assertion} succeeds with the given {@code time} and {@code unit}.
     * @param time The time in which the assertion must pass
     * @param unit The unit in which time is expressed
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
                if (now >= deadline) {
                    throw e;
                }
            }
            now = System.currentTimeMillis();
        } while (true);
    }

}
