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

import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
public class ClassNamePatternEventProcessorSelectorTest {

    @Test
    public void testMatchesPattern() throws Exception {
        String eventProcessor = mock(String.class);
        Pattern pattern = Pattern.compile("org\\.axonframework.*\\$MyEventListener");
        ClassNamePatternEventProcessorSelector
                testSubject = new ClassNamePatternEventProcessorSelector(pattern, eventProcessor);

        String actual = testSubject.selectEventProcessor(new MyEventListener());
        assertSame(eventProcessor, actual);
    }

    private static class MyEventListener implements EventListener {

        @Override
        public void handle(EventMessage event) {
        }
    }
}
