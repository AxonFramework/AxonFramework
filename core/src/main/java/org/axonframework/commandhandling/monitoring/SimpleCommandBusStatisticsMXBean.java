/*
 * Copyright (c) 2010. Axon Framework
 *
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

package org.axonframework.commandhandling.monitoring;

import org.axonframework.monitoring.Monitor;

import java.util.List;

/**
 * Management interface for the SimpleCommandBus monitor
 * <p/>
 * As required by the JMX specification. In combination with the implementation, this interface specifies and delivers
 * the actual JMX bean.
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public interface SimpleCommandBusStatisticsMXBean extends Monitor {
    /**
     * Returns the amount of registered handlers
     *
     * @return long representing the amount of handlers
     */
    long getCommandHandlerCount();

    /**
     * Returns a list with names of the registered handlers
     *
     * @return List of strings representing the names of registered handlers
     */
    List<String> getHandlerTypes();

    /**
     * Returns the amount of received commands
     *
     * @return long representing the amount of commands received
     */
    long getReceivedCommandCount();

    /**
     * Reset the amount of commands received counter
     */
    void resetReceivedCommandsCounter();

    /**
     * Returns true if the statistics are enabled
     * @return Boolean true if enabled
     */
    boolean isEnabled();

}
