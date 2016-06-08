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

import java.util.regex.Pattern;

/**
 * EventProcessorSelector implementation that chooses an EventProcessor based on whether the Listener's Class Name matches a given
 * Pattern.
 * <p/>
 * The given pattern must match the entire class name, not just part of it.
 * <p/>
 * Note that the name of the class used is the name of the class implementing the <code>EventListener</code> interface.
 * If a listener implements the <code>EventListenerProxy</code> interface, the value of the {@link
 * org.axonframework.eventhandling.EventListenerProxy#getTargetType()} is used. Annotated Event Listeners will always
 * have the actual annotated class name used.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ClassNamePatternEventHandlerManagerSelector extends AbstractEventHandlerManagerSelector {

    private final Pattern pattern;
    private final EventHandlerInvoker eventProcessor;

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the Event Processor Selector returns the given <code>defaultEventProcessor</code>.
     *  @param pattern The pattern to match an Event Listener's class name against
     * @param eventHandlerInvoker The eventProcessor to choose when the pattern matches
     */
    public ClassNamePatternEventHandlerManagerSelector(Pattern pattern, EventHandlerInvoker eventHandlerInvoker) {
        this.pattern = pattern;
        this.eventProcessor = eventHandlerInvoker;
    }

    @Override
    public EventHandlerInvoker doSelectEventHandlerManager(EventListener eventListener, Class listenerType) {
        String listenerName = listenerType.getName();
        if (pattern.matcher(listenerName).matches()) {
            return eventProcessor;
        }
        return null;
    }
}
