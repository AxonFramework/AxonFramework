/*
 * Copyright (c) 2010-2012. Axon Framework
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

import java.util.List;

/**
 * Management interface the SimpleEventBus monitor.
 * <p/>
 * Management interface as required by the JMX specification. In combination with the implementation, this interface
 * specifies and delivers the actual JMX bean.
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public interface SimpleEventBusStatisticsMXBean {

    /**
     * Returns the amount of registered listeners.
     *
     * @return long representing the amount of registered listeners
     */
    long getListenerCount();

    /**
     * Returns a list of simple class names (class name without its package) of the registered listeners. Multiple
     * listeners with the same name are supported
     *
     * @return List of string representing the names of the registered listeners
     */
    List<String> getListenerTypes();

    /**
     * Returns the amount of received events.
     *
     * @return long representing the amount of received events
     */
    long getReceivedEventsCount();

    /**
     * resets the amount of events received.
     */
    void resetReceivedEventsCount();
}