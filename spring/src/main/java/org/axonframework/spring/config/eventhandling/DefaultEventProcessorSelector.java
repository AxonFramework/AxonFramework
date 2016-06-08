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
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.PublishingEventProcessor;

/**
 * EventProcessorSelector implementation that always selects the same event processor. This implementation
 * can serve as delegate for other event processor selectors for event listeners that do not belong to a specific
 * event processor.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class DefaultEventProcessorSelector implements EventProcessorSelector {

    private static final String DEFAULT_PROCESSOR_IDENTIFIER = "default";
    private final EventProcessor defaultEventProcessor;

    /**
     * Initializes the DefaultEventProcessorSelector using a {@link PublishingEventProcessor} with
     * identifier "default", to which this instance will assign all Event Listeners.
     */
    public DefaultEventProcessorSelector() {
        this.defaultEventProcessor = new PublishingEventProcessor(DEFAULT_PROCESSOR_IDENTIFIER);
    }

    /**
     * Initializes the DefaultEventProcessorSelector to assign the given <code>defaultEventProcessor</code> to each listener.
     *
     * @param defaultEventProcessor The Event Processor to assign to each listener
     */
    public DefaultEventProcessorSelector(EventProcessor defaultEventProcessor) {
        Assert.notNull(defaultEventProcessor, "defaultEventProcessor may not be null");
        this.defaultEventProcessor = defaultEventProcessor;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation always returns the same instance of {@link PublishingEventProcessor}.
     */
    @Override
    public EventProcessor selectEventProcessor(EventListener eventListener) {
        return defaultEventProcessor;
    }
}
