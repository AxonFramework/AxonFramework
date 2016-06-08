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

import org.axonframework.common.Registration;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleEventBusTest {

    private EventProcessor listener1;
    private EventProcessor listener2;
    private EventBus testSubject;
    private EventProcessor listener3;

    @Before
    public void setUp() {
        listener1 = mock(EventProcessor.class);
        listener2 = mock(EventProcessor.class);
        listener3 = mock(EventProcessor.class);
        testSubject = new SimpleEventBus();
    }

    @Test
    public void testEventIsDispatchedToSubscribedListeners() throws Exception {
        testSubject.publish(newEvent());
        testSubject.subscribe(listener1);
        // subscribing twice should not make a difference
        Registration subscription1 = testSubject.subscribe(listener1);
        testSubject.publish(newEvent());
        Registration subscription2 = testSubject.subscribe(listener2);
        Registration subscription3 = testSubject.subscribe(listener3);
        testSubject.publish(newEvent());
        subscription1.close();
        testSubject.publish(newEvent());
        subscription2.close();
        subscription3.close();
        // unsubscribe a non-subscribed listener should not fail
        subscription3.close();
        testSubject.publish(newEvent());

        verify(listener1, times(2)).accept(anyList());
        verify(listener2, times(2)).accept(anyList());
        verify(listener3, times(2)).accept(anyList());
    }

    private EventMessage newEvent() {
        return new GenericEventMessage<>(new Object());
    }
}
