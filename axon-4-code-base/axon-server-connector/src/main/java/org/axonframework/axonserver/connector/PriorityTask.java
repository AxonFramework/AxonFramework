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

/**
 * Represents a task such as {@link Runnable} or {@link Comparable} that adheres to a priority by implementing
 * {@link Comparable}. Uses a combination of {@code priority} and {@code index} to compare between {@code this} and
 * other {@link PriorityTask} instances. A calculator (e.g.
 * {@link org.axonframework.axonserver.connector.command.CommandPriorityCalculator}) defines the priority of the task.
 * This task uses the {@code index} to differentiate between tasks with the same priority, ensuring the insert order is
 * leading in those scenarios.
 *
 * @author Stefan Dragisic
 * @author Milan Savic
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @see org.axonframework.axonserver.connector.command.CommandPriorityCalculator
 * @see org.axonframework.axonserver.connector.query.QueryPriorityCalculator
 * @since 4.6.0
 */
public interface PriorityTask extends Comparable<PriorityTask> {

    /**
     * Returns the priority of this task.
     *
     * @return The priority of this task.
     */
    long priority();

    /**
     * Returns the sequence of this task.
     *
     * @return The sequence of this task.
     */
    long sequence();

    @Override
    default int compareTo(PriorityTask that) {
        int c = Long.compare(this.priority(), that.priority());
        if (c != 0) {
            return -c;
        }
        return Long.compare(this.sequence(), that.sequence());
    }
}
