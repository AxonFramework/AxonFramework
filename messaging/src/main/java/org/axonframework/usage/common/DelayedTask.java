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

package org.axonframework.usage.common;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

import java.util.Objects;

/**
 * A utility class to run a task with a delay in a virtual thread. The task can be checked for its status (started,
 * finished, failed), and the failure cause can be retrieved if it failed.
 * <p>
 * This has severely lower overhead than using a {@link java.util.concurrent.ScheduledExecutorService} or similar
 * constructs, as it does not require a thread pool or scheduling mechanism. It simply runs the task in a virtual thread
 * after the specified delay. The virtual thread is parked for the duration of the delay, which allows it to be
 * lightweight and efficient.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class DelayedTask {

    private boolean started = false;
    private boolean finished = false;
    private boolean failed = false;
    private Exception failureCause = null;

    private DelayedTask(@Nonnull Runnable runnable, long delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Delay must be non-negative.");
        }
        Objects.requireNonNull(runnable, "The runnable must not be null.");
        Thread.ofVirtual().name("AxonIQ").start(() -> {
            try {
                Thread.sleep(delay);
                started = true;
                runnable.run();
            } catch (Exception e) {
                failed = true;
                failureCause = e;
            } finally {
                finished = true;
            }
        });
    }

    /**
     * Creates a new {@code DelayedTask} that runs the given {@code runnable} after the specified {@code delay}.
     *
     * @param runnable The task to run after the delay.
     * @param delay    The delay in milliseconds before the task is executed. Must be non-negative.
     * @return A new instance of {@code DelayedTask} that will run the given {@code runnable} after the specified
     * {@code delay}.
     */
    public static DelayedTask of(@Nonnull Runnable runnable, long delay) {
        return new DelayedTask(runnable, delay);
    }

    /**
     * Whether the task has started running.
     *
     * @return {@code true} if the task has started, {@code false} otherwise.
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Whether the task has finished running.
     *
     * @return {@code true} if the task has finished, {@code false} otherwise.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Whether the task has failed.
     *
     * @return {@code true} if the task has failed, {@code false} otherwise.
     */
    public boolean isFailed() {
        return failed;
    }

    /**
     * Gets the cause of the failure if the task has failed.
     *
     * @return The exception that caused the failure, or {@code null} if the task has not failed.
     */
    public Exception getFailureCause() {
        return failureCause;
    }
}
