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

package org.axonframework.commandhandling;

import org.axonframework.monitoring.Statistics;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jettro Coenradie
 */
public class SimpleCommandBusStatistics implements Statistics {
    private AtomicBoolean enabled = new AtomicBoolean(true);
    private AtomicLong amountOfHandlers = new AtomicLong(0);
    private AtomicLong amountOfReceivedCommands = new AtomicLong(0);
    private List<String> handlers = new CopyOnWriteArrayList<String>();

    /* construction */

    public SimpleCommandBusStatistics() {
        this(true);
    }

    public SimpleCommandBusStatistics(boolean enabled) {
        this.enabled.set(enabled);
    }

    /* getters */

    public long getAmountOfHandlers() {
        return amountOfHandlers.get();
    }

    public long getAmountOfReceivedCommands() {
        return amountOfReceivedCommands.get();
    }

    public List<String> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    /* operations */

    /**
     * TODO jettro : decide if we want to be able to enable or disable this.
     * <p/>
     * Indicate a new handler with the provided name is registered. Multiple handlers with the same name are
     * supported.
     *
     * @param name String representing the name of the handler to register
     */
    public void handlerRegistered(String name) {
        if (enabled.get()) {
            this.handlers.add(name);
            this.amountOfHandlers.incrementAndGet();
        }
    }

    /**
     * TODO jettro : decide if we want to be able to enable or disable this.
     * <p/>
     * Indicate a handler with the provided name is unregistered. In case multiple handlers with the same name are
     * registered, only one is unregistered. If no handler exists with the provided name no action is taken.
     *
     * @param name String representing the name of the handler to un-register
     */
    public void handlerUnregistered(String name) {
        if (enabled.get()) {
            this.handlers.remove(name);
            this.amountOfHandlers.decrementAndGet();
        }
    }

    /**
     * Indicate a new command is received. The statistics are only gathered if they are enabled.
     */
    public void newCommandReceived() {
        if (enabled.get()) {
            amountOfReceivedCommands.incrementAndGet();
        }
    }

    /**
     * TODO jettro : decide if we want to be able to enable or disable this.
     * <p/>
     * Resets the amount of commands received
     */
    public void resetCommandsReceived() {
        if (enabled.get()) {
            amountOfReceivedCommands.set(0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable() {
        this.enabled.set(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disable() {
        this.enabled.set(false);
    }
}
