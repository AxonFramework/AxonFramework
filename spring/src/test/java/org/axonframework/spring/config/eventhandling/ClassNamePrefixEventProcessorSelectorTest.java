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

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class ClassNamePrefixEventProcessorSelectorTest {

    @Test
    public void testLongestPrefixEvaluatedFirst() {
        String defaultEventProcessor = "default";
        String eventProcessor1 = "eventProcessor1";
        String eventProcessor2 = "eventProcessor2";

        Map<String, String> mappings = new HashMap<>();
        mappings.put("org.axonframework", eventProcessor1);
        mappings.put("org", eventProcessor2);
        mappings.put("$Proxy", eventProcessor2);
        ClassNamePrefixEventProcessorSelector
                selector = new ClassNamePrefixEventProcessorSelector(mappings, defaultEventProcessor);

        String actual = selector.selectEventProcessor(event -> {
        });
        assertSame(eventProcessor1, actual);
    }

    @Test
    public void testInitializeWithSingleMapping() {
        String eventProcessor1 = "eventProcessor1";

        ClassNamePrefixEventProcessorSelector
                selector = new ClassNamePrefixEventProcessorSelector("org.axonframework", eventProcessor1);

        String actual = selector.selectEventProcessor(event -> {
        });
        assertSame(eventProcessor1, actual);
    }

    @Test
    public void testRevertsToDefaultWhenNoMappingFound() {
        String defaultEventProcessor = "default";
        String eventProcessor1 = "eventProcessor";

        Map<String, String> mappings = new HashMap<>();
        mappings.put("javax.", eventProcessor1);
        ClassNamePrefixEventProcessorSelector
                selector = new ClassNamePrefixEventProcessorSelector(mappings, defaultEventProcessor);

        String actual = selector.selectEventProcessor(event -> {
        });
        assertSame(defaultEventProcessor, actual);
    }

    @Test
    public void testReturnsNullWhenNoMappingFound() {
        String eventProcessor1 = "eventProcessor1";

        Map<String, String> mappings = new HashMap<>();
        mappings.put("javax.", eventProcessor1);
        ClassNamePrefixEventProcessorSelector selector = new ClassNamePrefixEventProcessorSelector(mappings);

        String actual = selector.selectEventProcessor(event -> {
        });
        assertSame(null, actual);
    }
}
