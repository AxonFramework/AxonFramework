/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.update.detection;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.update.detection.TestEnvironmentDetector.AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT;
import static org.junit.jupiter.api.Assertions.*;

class TestEnvironmentDetectorTest {

    @AfterEach
    void cleanup() {
        // Reset the system property after each test to avoid side effects
        System.clearProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT);
    }

    @Test
    void shouldDetectTestEnvironment() {
        // This one is easy. It's a test, so it should be detected as a test environment.
        assertTrue(TestEnvironmentDetector.isTestEnvironment());
    }

    @Test
    void forceShouldDisableTestEnvironmentDetection() {
        // Force disable the test environment detection
        System.setProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT, "true");

        // Assert that the test environment is no longer detected
        assertFalse(TestEnvironmentDetector.isTestEnvironment());
    }

    @Test
    void shouldSeeNonTestEnvironment() {
        AtomicReference<Boolean> isTestEnvironment = new AtomicReference<>(null);

        // If we launch a new thread, the stacktrace won't contain the junit class, so it should not be detected as a test environment.
        Thread nonTestThread = new Thread(() -> {
            isTestEnvironment.set(TestEnvironmentDetector.isTestEnvironment());
        });
        nonTestThread.start();
        try {
            nonTestThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Thread was interrupted", e);
        }
        // Assert that the thread is not detected as a test environment
        assertFalse(isTestEnvironment.get(), "The thread should not be detected as a test environment,");
    }
}