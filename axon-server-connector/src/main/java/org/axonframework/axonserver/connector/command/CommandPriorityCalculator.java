/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.command;

import org.axonframework.commandhandling.CommandMessage;

/**
 * Calculate priority of message based on it content. Higher value means higher priority.
 *
 * @author Marc Gathier
 * @since 3.4
 */
@FunctionalInterface
public interface CommandPriorityCalculator {

    /**
     * Determines the priority of the given {@code command}.
     *
     * @param command a {@link CommandMessage} to prioritize
     * @return an {@code int} defining the priority of the given {@code command}
     */
    int determinePriority(CommandMessage<?> command);

    /**
     * Returns a default implementation of the {@link CommandPriorityCalculator}, always returning priority {@code 0}.
     *
     * @return a lambda taking in a {@link CommandMessage} to prioritize to the default of priority {@code 0}
     */
    static CommandPriorityCalculator defaultCommandPriorityCalculator() {
        return command -> 0;
    }
}
