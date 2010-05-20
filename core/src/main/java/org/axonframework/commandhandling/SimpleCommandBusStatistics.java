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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jettro Coenradie
 */
public class SimpleCommandBusStatistics {
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

    public void handlerRegistered(String name) {
        if (enabled.get()) {
            this.handlers.add(name);
            this.amountOfHandlers.incrementAndGet();
        }
    }

    public void handlerUnregistered(String name) {
        if (enabled.get()) {
            this.handlers.remove(name);
            this.amountOfHandlers.decrementAndGet();
        }
    }

    public void newCommandReceived() {
        if (enabled.get()) {
            amountOfReceivedCommands.incrementAndGet();
        }
    }

    public void resetCommandsReceived() {
        if (enabled.get()) {
            amountOfReceivedCommands.set(0);
        }
    }

    public void setEnabled() {
        this.enabled.set(true);
    }

    public void setDisabled() {
        this.enabled.set(false);
    }

}
