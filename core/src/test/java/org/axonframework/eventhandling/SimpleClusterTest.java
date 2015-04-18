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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleClusterTest {

    private SimpleCluster testSubject;
    private EventListener eventListener;

    @Before
    public void setUp() throws Exception {
        testSubject = new SimpleCluster("cluster");
        eventListener = mock(EventListener.class);
    }

    @Test
    public void testSubscribeMember() {
        testSubject.subscribe(eventListener);
        assertEquals(1, testSubject.getMembers().size());
    }

    @Test
    public void testSubscribeOrderedMembers() {
        OrderResolver mockOrderResolver = mock(OrderResolver.class);
        EventListener eventListener2 = mock(EventListener.class);
        when(mockOrderResolver.orderOf(eventListener)).thenReturn(1);
        when(mockOrderResolver.orderOf(eventListener2)).thenReturn(2);
        testSubject = new SimpleCluster("cluster", mockOrderResolver);
        testSubject.subscribe(eventListener2);
        testSubject.subscribe(eventListener);
        assertEquals(2, testSubject.getMembers().size());
        // the eventListener instance must come first
        assertEquals(eventListener, testSubject.getMembers().iterator().next());
    }

    @Test
    public void testUnsubscribeMember() {
        assertEquals(0, testSubject.getMembers().size());
        testSubject.subscribe(eventListener);
        testSubject.unsubscribe(eventListener);
        assertEquals(0, testSubject.getMembers().size());
    }

    @Test
    public void testMetaDataAvailable() {
        assertNotNull(testSubject.getMetaData());
    }

    @Test
    public void testPublishEvent() {
        testSubject.subscribe(eventListener);
        EventMessage event = new GenericEventMessage<>(new Object());
        testSubject.handle(event);

        verify(eventListener).handle(event);
    }
}
