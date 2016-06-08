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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class ClassNamePrefixEventHandlerManagerSelectorTest {

    @Test
    public void testLongestPrefixEvaluatedFirst() {
        EventHandlerInvoker defaultEventProcessor = new SimpleEventHandlerInvoker("default");
        EventHandlerInvoker eventProcessor1 = new SimpleEventHandlerInvoker("eventProcessor1");
        EventHandlerInvoker eventProcessor2 = new SimpleEventHandlerInvoker("eventProcessor2");

        Map<String, EventHandlerInvoker> mappings = new HashMap<>();
        mappings.put("org.axonframework", eventProcessor1);
        mappings.put("org", eventProcessor2);
        mappings.put("$Proxy", eventProcessor2);
        ClassNamePrefixEventHandlerManagerSelector
                selector = new ClassNamePrefixEventHandlerManagerSelector(mappings, defaultEventProcessor);

        EventHandlerInvoker actual = selector.selectHandlerManager(event -> {
        });
        assertSame(eventProcessor1, actual);
    }

    @Test
    public void testInitializeWithSingleMapping() {
        EventHandlerInvoker eventProcessor1 = new SimpleEventHandlerInvoker("eventProcessor1");

        ClassNamePrefixEventHandlerManagerSelector
                selector = new ClassNamePrefixEventHandlerManagerSelector("org.axonframework", eventProcessor1);

        EventHandlerInvoker actual = selector.selectHandlerManager(event -> {
        });
        assertSame(eventProcessor1, actual);
    }

    @Test
    public void testRevertsToDefaultWhenNoMappingFound() {
        EventHandlerInvoker defaultEventProcessor = new SimpleEventHandlerInvoker("default");
        EventHandlerInvoker eventProcessor1 = new SimpleEventHandlerInvoker("eventProcessor");

        Map<String, EventHandlerInvoker> mappings = new HashMap<>();
        mappings.put("javax.", eventProcessor1);
        ClassNamePrefixEventHandlerManagerSelector
                selector = new ClassNamePrefixEventHandlerManagerSelector(mappings, defaultEventProcessor);

        EventHandlerInvoker actual = selector.selectHandlerManager(event -> {
        });
        assertSame(defaultEventProcessor, actual);
    }

    @Test
    public void testReturnsNullWhenNoMappingFound() {
        EventHandlerInvoker eventProcessor1 = new SimpleEventHandlerInvoker("eventProcessor1");

        Map<String, EventHandlerInvoker> mappings = new HashMap<>();
        mappings.put("javax.", eventProcessor1);
        ClassNamePrefixEventHandlerManagerSelector selector = new ClassNamePrefixEventHandlerManagerSelector(mappings);

        EventHandlerInvoker actual = selector.selectHandlerManager(event -> {
        });
        assertSame(null, actual);
    }
}
