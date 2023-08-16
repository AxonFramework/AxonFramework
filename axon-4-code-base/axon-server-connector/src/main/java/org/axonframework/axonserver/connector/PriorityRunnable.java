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

package org.axonframework.axonserver.connector;

import java.util.Objects;

/**
 * A wrapper class of {@link Runnable Runnables} that adheres to a priority by implementing {@link PriorityTask}. Uses a
 * combination of {@code priority} and {@code index} to compare between {@code this} and other priority task instances.
 * A calculator (e.g. {@link org.axonframework.axonserver.connector.command.CommandPriorityCalculator}) defines the
 * priority of the task. This task uses the {@code index} to differentiate between tasks with the same priority,
 * ensuring the insert order is leading in those scenarios.
 *
 * @author Stefan Dragisic
 * @author Milan Savic
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see org.axonframework.axonserver.connector.command.CommandPriorityCalculator
 * @see org.axonframework.axonserver.connector.query.QueryPriorityCalculator
 * @since 4.6.0
 */
public class PriorityRunnable implements Runnable, PriorityTask {

    private final Runnable task;
    private final long priority;
    private final long sequence;

    /**
     * Construct a priority task.
     *
     * @param task     The {@link Runnable} that should be executed with a {@code priority}.
     * @param priority The priority of the {@code task} to execute, dedicating the order among tasks.
     * @param sequence The sequence of the {@code task} to execute, dedicating the order among equal {@code priority}
     *                 tasks.
     */
    public PriorityRunnable(Runnable task, long priority, long sequence) {
        this.task = task;
        this.priority = priority;
        this.sequence = sequence;
    }

    @Override
    public void run() {
        task.run();
    }

    public long priority() {
        return priority;
    }

    public long sequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PriorityRunnable that = (PriorityRunnable) o;
        return priority == that.priority && sequence == that.sequence && Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task, priority, sequence);
    }

    @Override
    public String toString() {
        return "PriorityRunnable{" +
                "task=" + task +
                ", priority=" + priority +
                ", sequence=" + sequence +
                '}';
    }
}
