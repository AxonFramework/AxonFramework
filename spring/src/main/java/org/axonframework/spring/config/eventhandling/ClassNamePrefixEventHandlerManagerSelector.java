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

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * EventProcessorSelector implementation that chooses an EventProcessor based on a mapping of the Listener's Class Name. It maps a
 * prefix to an EventProcessor. When two prefixes match the same class, the EventProcessor mapped to the longest prefix is chosen.
 * <p/>
 * For example, consider the following mappings: <pre>
 * org.axonframework -> eventProcessor1
 * com.              -> eventProcessor2
 * com.somecompany   -> eventProcessor3
 * </pre>
 * A class named <code>com.somecompany.SomeListener</code> will map to <code>eventProcessor3</code> as it is mapped to a more
 * specific prefix. This implementation uses {@link String#startsWith(String)} to evaluate a match.
 * <p/>
 * Note that the name of the class used is the name of the class implementing the <code>EventListener</code> interface.
 * If a listener implements the <code>EventListenerProxy</code> interface, the value of the {@link
 * org.axonframework.eventhandling.EventListenerProxy#getTargetType()} is used. Annotated Event Listeners will always
 * have the actual annotated class name used.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ClassNamePrefixEventHandlerManagerSelector extends AbstractEventHandlerManagerSelector {

    private final Map<String, EventHandlerInvoker> mappings;
    private final EventHandlerInvoker defaultEventHandlerInvoker;

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector where classes starting with the given <code>prefix</code> will be
     * mapped to the given <code>eventProcessor</code>.
     * <p/>
     * This method is identical to {@link #ClassNamePrefixEventHandlerManagerSelector(java.util.Map)} with only a single mapping.
     *  @param prefix  The prefix of the fully qualified classname to match against
     * @param eventHandlerInvoker The event processor to choose if a match is found
     */
    public ClassNamePrefixEventHandlerManagerSelector(String prefix, EventHandlerInvoker eventHandlerInvoker) {
        this(Collections.singletonMap(prefix, eventHandlerInvoker));
    }

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the EventProcessor Selector returns <code>null</code>.
     *
     * @param mappings the mappings defining an event processor for each Class Name prefix
     */
    public ClassNamePrefixEventHandlerManagerSelector(Map<String, EventHandlerInvoker> mappings) {
        this(mappings, null);
    }

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the Event Processor Selector returns the given <code>defaultEventProcessor</code>.
     *  @param mappings       the mappings defining an event processor for each Class Name prefix
     * @param defaultEventHandlerInvoker The event processor to use when no mapping is present*/
    public ClassNamePrefixEventHandlerManagerSelector(Map<String, EventHandlerInvoker> mappings, EventHandlerInvoker defaultEventHandlerInvoker) {
        this.defaultEventHandlerInvoker = defaultEventHandlerInvoker;
        this.mappings = new TreeMap<>(new ReverseStringComparator());
        this.mappings.putAll(mappings);
    }

    @Override
    public EventHandlerInvoker doSelectEventHandlerManager(EventListener eventListener, Class<?> listenerType) {
        String listenerName = listenerType.getName();
        for (Map.Entry<String, EventHandlerInvoker> entry : mappings.entrySet()) {
            if (listenerName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return defaultEventHandlerInvoker;
    }

    private static class ReverseStringComparator implements Comparator<String>, Serializable {

        private static final long serialVersionUID = -1653838988719816515L;

        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }
}
