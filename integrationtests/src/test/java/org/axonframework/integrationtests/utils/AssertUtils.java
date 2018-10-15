/*
 * Copyright (c) 2010-2018. Axon Framework
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
