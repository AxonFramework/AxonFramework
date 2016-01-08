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
import org.axonframework.eventhandling.EventProcessor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CompositeEventProcessorSelectorTest {

    private CompositeEventProcessorSelector testSubject;
    private EventListener mockListener;
    private EventProcessorSelector selector1;
    private EventProcessorSelector selector2;
    private EventProcessorSelector selector3;
    private EventProcessor eventProcessor;

    @Before
    public void setUp() throws Exception {
        mockListener = mock(EventListener.class);
        selector1 = mock(EventProcessorSelector.class);
        selector2 = mock(EventProcessorSelector.class);
        selector3 = mock(EventProcessorSelector.class);
        eventProcessor = mock(EventProcessor.class);
    }

    @Test
    public void testSelectorDelegatesInOrder() throws Exception {
        EventProcessor eventProcessor = mock(EventProcessor.class);

        when(selector2.selectEventProcessor(isA(EventListener.class))).thenReturn(eventProcessor);
        testSubject = new CompositeEventProcessorSelector(Arrays.asList(selector1, selector2, selector3));

        EventProcessor actual = testSubject.selectEventProcessor(mockListener);
        assertSame(eventProcessor, actual);
        verify(selector1).selectEventProcessor(mockListener);
        verify(selector2).selectEventProcessor(mockListener);
        verify(selector3, never()).selectEventProcessor(any(EventListener.class));
    }

    @Test
    public void testSelectorDelegatesInOrder_NoEventProcessorFound() throws Exception {
        testSubject = new CompositeEventProcessorSelector(Arrays.asList(selector1, selector2, selector3));

        EventProcessor actual = testSubject.selectEventProcessor(mockListener);
        assertNull(actual);
        verify(selector1).selectEventProcessor(mockListener);
        verify(selector2).selectEventProcessor(mockListener);
        verify(selector3).selectEventProcessor(mockListener);
    }
}
