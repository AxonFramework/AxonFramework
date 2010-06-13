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

import org.axonframework.monitoring.Statistics;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>Statistics object to store information about the internal of the <code>SimpleEventBus</code>.</p> <p>You can
 * request information about the registered listeners but also about the number of received events.</p> <p>Next to
 * requesting information it is also possible to reset the counters</p> <p>Finally, the statistics are only gathered
 * when explicitly enabled. By default they are switched off.</p>
 *
 * @author Jettro Coenradie
 * @see SimpleEventBus
 * @since 0.6
 */
public class SimpleEventBusStatistics implements Statistics {

    private volatile boolean enabled = false;
    private AtomicLong listenerCount = new AtomicLong(0);
    private AtomicLong publishedEventCounter = new AtomicLong(0);
    private List<String> listeners = new CopyOnWriteArrayList<String>();

    /**
     * Returns the amount of registered listeners
     *
     * @return long representing the amount of listeners registered
     */
    public long getListenerCount() {
        return listenerCount.get();
    }

    /**
     * Returns the list of names of the registered listeners
     *
     * @return List of strings representing the names of the registered listeners
     */
    public List<String> listeners() {
        return Collections.unmodifiableList(listeners);
    }

    /**
     * Returns the amount of received events, from the beginning or after the last reset
     *
     * @return long representing the amount events received
     */
    public long getPublishedEventCounter() {
        return publishedEventCounter.get();
    }

    /**
     * Indicate that a new listener is registered by providing it's name. It is possible to store multiple listeners
     * with the same name.
     *
     * @param name String representing the name of the registered listener
     */
    public void listenerRegistered(String name) {
        this.listeners.add(name);
        this.listenerCount.incrementAndGet();
    }

    /**
     * Indicate that a listener is unregistered with the provided name. If multiple listeners with the same name exist
     * only one listener is removed. No action is taken when the provided name does not exist.
     *
     * @param name String representing the name of the listener to un-register.
     */
    public void recordUnregisteredListener(String name) {
        this.listeners.remove(name);
        this.listenerCount.decrementAndGet();
    }

    /**
     * Indicate that a new event is received. Statistics are only gathered if enabled
     */
    public void recordPublishedEvent() {
        if (enabled) {
            publishedEventCounter.incrementAndGet();
        }
    }

    /**
     * Reset the amount of events that was received
     */
    public void resetReceivedEventCount() {
        publishedEventCounter.set(0);
    }

    /**
     * Indicates whether this statistics instance is enabled
     *
     * @return <code>true</code> if this statistics instance is enabled, otherwise <code>false</code>.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables statistics registration
     */
    @Override
    public void enable() {
        this.enabled = true;
    }

    /**
     * Disables statistics registration
     */
    @Override
    public void disable() {
        this.enabled = false;
    }
}
