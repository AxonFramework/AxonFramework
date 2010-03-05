/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler;

import org.axonframework.core.StubDomainEvent;
import org.junit.*;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleEventBusTest {

    private EventListener listener1;
    private EventListener listener2;
    private EventBus testSubject;
    private EventListener listener3;

    @Before
    public void setUp() {
        listener1 = mock(EventListener.class);
        listener2 = mock(EventListener.class);
        listener3 = mock(EventListener.class);
        testSubject = new SimpleEventBus();
    }

    @Test
    public void testEventIsDispatchedToSubscribedListeners() {
        testSubject.publish(new StubDomainEvent());
        testSubject.subscribe(listener1);
        // subscribing twice should not make a difference
        testSubject.subscribe(listener1);
        testSubject.publish(new StubDomainEvent());
        testSubject.subscribe(listener2);
        testSubject.subscribe(listener3);
        testSubject.publish(new StubDomainEvent());
        testSubject.unsubscribe(listener1);
        testSubject.publish(new StubDomainEvent());
        testSubject.unsubscribe(listener2);
        testSubject.unsubscribe(listener3);
        // unsubscribe a non-subscribed listener should not fail
        testSubject.unsubscribe(listener3);
        testSubject.publish(new StubDomainEvent());

        verify(listener1, times(2)).handle(isA(StubDomainEvent.class));
        verify(listener2, times(2)).handle(isA(StubDomainEvent.class));
        verify(listener3, times(2)).handle(isA(StubDomainEvent.class));
    }
}
