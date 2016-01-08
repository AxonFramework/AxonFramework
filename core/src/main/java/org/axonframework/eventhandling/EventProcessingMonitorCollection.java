/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Registration;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Implementation of the EventProcessingMonitor that delegates to all registered EventProcessingMonitor instances.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class EventProcessingMonitorCollection implements EventProcessingMonitor, EventProcessingMonitorSupport {

    private final Set<EventProcessingMonitor> delegates = new CopyOnWriteArraySet<>();

    @Override
    public void onEventProcessingCompleted(List<? extends EventMessage> eventMessages) {
        for (EventProcessingMonitor delegate : delegates) {
            delegate.onEventProcessingCompleted(eventMessages);
        }
    }

    @Override
    public void onEventProcessingFailed(List<? extends EventMessage> eventMessages, Throwable cause) {
        for (EventProcessingMonitor delegate : delegates) {
            delegate.onEventProcessingFailed(eventMessages, cause);
        }
    }

    @Override
    public Registration subscribeEventProcessingMonitor(EventProcessingMonitor monitor) {
        delegates.add(monitor);
        return () -> delegates.remove(monitor);
    }
}
