/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.StandardMBean;

/**
 * <p>Statistics object to store information about the internals of the <code>SimpleCommandBus</code>.</p> <p>You can
 * request information about the registered handlers but also about the number of received commands.</p> <p>Next to
 * requesting information it is also possible to reset the counters</p> <p>Finally, the statistics are only gathered
 * when explicitly enabled. By default they are switched off.</p>
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 0.6
 */
class SimpleCommandBusStatistics extends StandardMBean implements SimpleCommandBusStatisticsMXBean {

    private AtomicLong handlerCounter = new AtomicLong(0);
    private AtomicLong receivedCommandCounter = new AtomicLong(0);
    private List<String> handlerTypes = new CopyOnWriteArrayList<String>();

    /**
     * Creates an instance of this statistics MBean.
     */
    public SimpleCommandBusStatistics() {
        super(SimpleCommandBusStatisticsMXBean.class, true);
    }

    /**
     * Returns the amount of registered handlers.
     *
     * @return long representing the amount of registered handlers
     */
    @Override
    public long getCommandHandlerCount() {
        return handlerCounter.get();
    }

    /**
     * Returns the amount of received commands from the beginning of starting up or after the last reset.
     *
     * @return long representing the amount of received commands
     */
    @Override
    public long getReceivedCommandCount() {
        return receivedCommandCounter.get();
    }

    /**
     * Returns a list with the names of the types of the registered handlers.
     *
     * @return List of strings with the names of the registered handlers
     */
    @Override
    public List<String> getHandlerTypes() {
        return Collections.unmodifiableList(handlerTypes);
    }

    /**
     * Resets the received command counter.
     */
    @Override
    public void resetReceivedCommandsCounter() {
        receivedCommandCounter.set(0);
    }

    /**
     * Indicate a new handler with the provided name is registered. Multiple handlers with the same name are supported.
     *
     * @param name String representing the name of the handler to register
     */
    void reportHandlerRegistered(String name) {
        this.handlerTypes.add(name);
        this.handlerCounter.incrementAndGet();
    }

    /**
     * Indicate a handler with the provided name is unregistered. In case multiple handlers with the same name are
     * registered, only one is unregistered. If no handler exists with the provided name no action is taken.
     *
     * @param name String representing the name of the handler to un-register
     */
    void recordUnregisteredHandler(String name) {
        this.handlerTypes.remove(name);
        this.handlerCounter.decrementAndGet();
    }

    /**
     * Indicate a new command is received. The statistics are only gathered if they are enabled.
     */
    void recordReceivedCommand() {
        receivedCommandCounter.incrementAndGet();
    }
}
