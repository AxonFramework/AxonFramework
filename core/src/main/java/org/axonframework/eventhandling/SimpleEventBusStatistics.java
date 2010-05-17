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

package org.axonframework.eventhandling;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jettro Coenradie
 */
public class SimpleEventBusStatistics {
    private AtomicBoolean enabled = new AtomicBoolean(true);
    private AtomicLong amountOfListeners = new AtomicLong(0);
    private AtomicLong amountOfReceivedEvents = new AtomicLong(0);
    private List<String> listeners  = new CopyOnWriteArrayList<String>();

    /* construction */
    public SimpleEventBusStatistics() {
        this(true);
    }

    public SimpleEventBusStatistics(boolean enabled) {
        this.enabled.set(enabled);
    }

    /* getters */
    public long getAmountOfListeners() {
        return amountOfListeners.get();
    }

    public List<String> listeners() {
        return Collections.unmodifiableList(listeners);
    }

    public long getAmountOfReceivedEvents() {
        return amountOfReceivedEvents.get();
    }

    /* operations */
    public void listenerRegistered(String name) {
        if (enabled.get()) {
            this.listeners.add(name);
            this.amountOfListeners.incrementAndGet();
        }
    }

    public void listenerUnregistered(String name) {
        if (enabled.get()) {
            this.listeners.remove(name);
            this.amountOfListeners.decrementAndGet();
        }
    }

    public void newEventReceived() {
        if (enabled.get()) {
            amountOfReceivedEvents.incrementAndGet();
        }
    }

    public void resetEventsReceived() {
        if (enabled.get()) {
            amountOfReceivedEvents.set(0);
        }
    }

    public void setEnabled() {
        this.enabled.set(true);
    }

    public void setDisabled() {
        this.enabled.set(false);
    }
}
