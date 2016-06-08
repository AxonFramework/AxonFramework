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

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;

/**
 * EventProcessorSelector implementation that always selects the same event processor. This implementation
 * can serve as delegate for other event processor selectors for event listeners that do not belong to a specific
 * event processor.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class DefaultEventHandlerManagerSelector implements EventHandlerManagerSelector {

    private static final String DEFAULT_MANAGER_IDENTIFIER = "default";
    private final EventHandlerInvoker defaultEventHandlerInvoker;

    /**
     * Initializes the DefaultEventProcessorSelector using a {@link SimpleEventHandlerInvoker} with
     * identifier "default", to which this instance will assign all Event Listeners.
     */
    public DefaultEventHandlerManagerSelector() {
        this.defaultEventHandlerInvoker = new SimpleEventHandlerInvoker(DEFAULT_MANAGER_IDENTIFIER);
    }

    /**
     * Initializes the DefaultEventProcessorSelector to assign the given <code>defaultEventHandlerManager</code> to each listener.
     *
     * @param defaultEventHandlerInvoker The Event Processor to assign to each listener
     */
    public DefaultEventHandlerManagerSelector(EventHandlerInvoker defaultEventHandlerInvoker) {
        Assert.notNull(defaultEventHandlerInvoker, "defaultEventHandlerManager may not be null");
        this.defaultEventHandlerInvoker = defaultEventHandlerInvoker;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation always returns the same instance of {@link SimpleEventHandlerInvoker}.
     */
    @Override
    public EventHandlerInvoker selectHandlerManager(EventListener eventListener) {
        return defaultEventHandlerInvoker;
    }
}
