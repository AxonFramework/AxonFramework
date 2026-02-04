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

package org.axonframework.update.common;

import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DelayedTask}.
 *
 * @author Mitchell Herrijgers
 */
class DelayedTaskTest {

    @Test
    void taskExecutesAfterDelay() {
        long delay = 200;
        long start = System.currentTimeMillis();
        final boolean[] ran = {false};
        DelayedTask task = DelayedTask.of(() -> ran[0] = true, delay);

        await("Await finished Delayed Task").pollDelay(Duration.ofMillis(10))
                                            .atMost(Duration.ofMillis(500))
                                            .until(task::isFinished);

        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= delay, "Task should execute after at least the specified delay");
        assertTrue(ran[0], "Task should have run");
        assertTrue(task.isStarted(), "Task should be marked as started");
        assertTrue(task.isFinished(), "Task should be marked as finished");
        assertFalse(task.isFailed(), "Task should not be marked as failed");
        assertNull(task.getFailureCause(), "Failure cause should be null");
    }

    @Test
    void taskFailureSetsFailedFlagAndCause() {
        long delay = 100;
        RuntimeException expected = new RuntimeException("fail");
        // The array is to avoid 'variable not initialized' compiler error
        DelayedTask[] task = new DelayedTask[1];
        task[0] = DelayedTask.of(() -> {
            // Assert statuses are correct while the task is running
            assertTrue(task[0].isStarted(), "Task should be started while executing");
            assertFalse(task[0].isFinished(), "Task should not be finished while executing");
            throw expected;
        }, delay);

        await("Await finished Delayed Task").pollDelay(Duration.ofMillis(10))
                                            .atMost(Duration.ofMillis(500))
                                            .until(task[0]::isFinished);

        assertTrue(task[0].isStarted(), "Task should be marked as started");
        assertTrue(task[0].isFinished(), "Task should be marked as finished");
        assertTrue(task[0].isFailed(), "Task should be marked as failed");
        assertEquals(expected, task[0].getFailureCause(), "Failure cause should match thrown exception");
    }

    @Test
    void taskWithZeroDelayRunsImmediately() {
        final boolean[] ran = {false};
        DelayedTask task = DelayedTask.of(() -> ran[0] = true, 0);

        await("Await finished Delayed Task").pollDelay(Duration.ofMillis(5))
                                            .atMost(Duration.ofMillis(500))
                                            .until(task::isFinished);

        assertTrue(ran[0], "Task should have run with zero delay");
        assertTrue(task.isStarted(), "Task should be marked as started");
        assertTrue(task.isFinished(), "Task should be marked as finished");
        assertFalse(task.isFailed(), "Task should not be marked as failed");
    }

    @Test
    void taskWithNegativeDelayThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> DelayedTask.of(() -> {}, -100),
                     "Task with negative delay should throw IllegalArgumentException");
    }
}
