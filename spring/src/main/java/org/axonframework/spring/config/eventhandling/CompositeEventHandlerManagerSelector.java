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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * EventProcessorSelector implementation that delegates the selection to a list of other EventProcessorSelectors. The first of the
 * delegates is asked for an EventProcessor. If it provides none, the second is invoked, and so forth, until a delegate returns
 * an EventProcessor instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CompositeEventHandlerManagerSelector implements EventHandlerManagerSelector {

    private final List<EventHandlerManagerSelector> delegates;

    /**
     * Initializes the CompositeEventProcessorSelector with the given List of <code>delegates</code>. The delegates are
     * evaluated in the order provided by the List's iterator.
     *
     * @param delegates the delegates to evaluate
     */
    public CompositeEventHandlerManagerSelector(List<EventHandlerManagerSelector> delegates) {
        this.delegates = new ArrayList<>(delegates);
    }

    @Override
    public EventHandlerInvoker selectHandlerManager(EventListener eventListener) {
        EventHandlerInvoker selected = null;
        Iterator<EventHandlerManagerSelector> iterator = delegates.iterator();
        while (selected == null && iterator.hasNext()) {
            selected = iterator.next().selectHandlerManager(eventListener);
        }
        return selected;
    }
}
