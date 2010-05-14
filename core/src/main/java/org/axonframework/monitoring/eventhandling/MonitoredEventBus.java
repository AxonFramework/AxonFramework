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

package org.axonframework.monitoring.eventhandling;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.monitoring.Monitored;

/**
 * @author Jettro Coenradie
 */
public class MonitoredEventBus implements EventBus, Monitored<EventBusMonitor> {

    private EventBus wrappedEventBus;
    private EventBusMonitor eventBusMonitor;

    public MonitoredEventBus(EventBus wrappedEventBus) {
        this.wrappedEventBus = wrappedEventBus;
        this.eventBusMonitor = new EventBusMonitor();
    }

    @Override
    public void publish(Event event) {
        wrappedEventBus.publish(event);

        eventBusMonitor.notifyEventDispatched();
    }

    @Override
    public void subscribe(EventListener eventListener) {
        wrappedEventBus.subscribe(eventListener);

        eventBusMonitor.notifyNewListenerAvailable();
    }

    @Override
    public void unsubscribe(EventListener eventListener) {
        wrappedEventBus.unsubscribe(eventListener);

        eventBusMonitor.notifyListenerRemoved();
    }

    /* management extensions */

    @Override
    public EventBusMonitor requestMonitor() {
        return eventBusMonitor;
    }

}
