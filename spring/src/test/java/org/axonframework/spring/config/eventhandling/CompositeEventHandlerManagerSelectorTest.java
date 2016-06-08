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
import org.axonframework.eventhandling.EventListener;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CompositeEventHandlerManagerSelectorTest {

    private CompositeEventHandlerManagerSelector testSubject;
    private EventListener mockListener;
    private EventHandlerManagerSelector selector1;
    private EventHandlerManagerSelector selector2;
    private EventHandlerManagerSelector selector3;

    @Before
    public void setUp() throws Exception {
        mockListener = mock(EventListener.class);
        selector1 = mock(EventHandlerManagerSelector.class);
        selector2 = mock(EventHandlerManagerSelector.class);
        selector3 = mock(EventHandlerManagerSelector.class);
    }

    @Test
    public void testSelectorDelegatesInOrder() throws Exception {
        EventHandlerInvoker eventProcessor = mock(EventHandlerInvoker.class);

        when(selector2.selectHandlerManager(isA(EventListener.class))).thenReturn(eventProcessor);
        testSubject = new CompositeEventHandlerManagerSelector(Arrays.asList(selector1, selector2, selector3));

        EventHandlerInvoker actual = testSubject.selectHandlerManager(mockListener);
        assertSame(eventProcessor, actual);
        verify(selector1).selectHandlerManager(mockListener);
        verify(selector2).selectHandlerManager(mockListener);
        verify(selector3, never()).selectHandlerManager(any(EventListener.class));
    }

    @Test
    public void testSelectorDelegatesInOrder_NoEventProcessorFound() throws Exception {
        testSubject = new CompositeEventHandlerManagerSelector(Arrays.asList(selector1, selector2, selector3));

        EventHandlerInvoker actual = testSubject.selectHandlerManager(mockListener);
        assertNull(actual);
        verify(selector1).selectHandlerManager(mockListener);
        verify(selector2).selectHandlerManager(mockListener);
        verify(selector3).selectHandlerManager(mockListener);
    }
}
