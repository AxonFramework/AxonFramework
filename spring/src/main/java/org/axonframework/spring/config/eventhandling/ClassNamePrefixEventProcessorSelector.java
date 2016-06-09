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
public class ClassNamePrefixEventProcessorSelector extends AbstractEventProcessorSelector {

    private final Map<String, String> eventProcessorToPrefix;
    private final String defaultEventProcessor;

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector where classes starting with the given <code>prefix</code> will be
     * mapped to the given <code>eventProcessor</code>.
     * <p/>
     * This method is identical to {@link #ClassNamePrefixEventProcessorSelector(java.util.Map)} with only a single mapping.
     * @param prefix  The prefix of the fully qualified classname to match against
     * @param eventProcessor The event processor to choose if a match is found
     */
    public ClassNamePrefixEventProcessorSelector(String prefix, String eventProcessor) {
        this(Collections.singletonMap(prefix, eventProcessor));
    }

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the EventProcessor Selector returns <code>null</code>.
     *
     * @param eventProcessorToPrefix the mappings defining an event processor for each Class Name prefix
     */
    public ClassNamePrefixEventProcessorSelector(Map<String, String> eventProcessorToPrefix) {
        this(eventProcessorToPrefix, null);
    }

    /**
     * Initializes the ClassNamePrefixEventProcessorSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the Event Processor Selector returns the given <code>defaultEventProcessor</code>.
     * @param eventProcessorToPrefix       the mappings defining an event processor for each Class Name prefix
     * @param defaultEventProcessor The event processor to use when no mapping is present*/
    public ClassNamePrefixEventProcessorSelector(Map<String, String> eventProcessorToPrefix, String defaultEventProcessor) {
        this.defaultEventProcessor = defaultEventProcessor;
        this.eventProcessorToPrefix = new TreeMap<>(new ReverseStringComparator());
        this.eventProcessorToPrefix.putAll(eventProcessorToPrefix);
    }

    @Override
    public String doSelectEventHandlerManager(EventListener eventListener, Class<?> listenerType) {
        String listenerName = listenerType.getName();
        for (Map.Entry<String, String> entry : eventProcessorToPrefix.entrySet()) {
            if (listenerName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return defaultEventProcessor;
    }

    private static class ReverseStringComparator implements Comparator<String>, Serializable {

        private static final long serialVersionUID = -1653838988719816515L;

        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }
}
