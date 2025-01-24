/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleEventBus}
 *
 * @author Allard Buijze
 */
class SimpleEventBusTest {

    private Consumer<List<? extends EventMessage<?>>> listener1;
    private Consumer<List<? extends EventMessage<?>>> listener2;
    private Consumer<List<? extends EventMessage<?>>> listener3;

    private EventBus testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        listener1 = mock(Consumer.class);
        //noinspection unchecked
        listener2 = mock(Consumer.class);
        //noinspection unchecked
        listener3 = mock(Consumer.class);

        testSubject = SimpleEventBus.builder().build();
    }

    @Test
    void eventIsDispatchedToSubscribedListeners() {
        testSubject.publish(newEvent());
        testSubject.subscribe(listener1);
        // subscribing twice should not make a difference
        Registration subscription1 = testSubject.subscribe(listener1);
        testSubject.publish(newEvent());
        Registration subscription2 = testSubject.subscribe(listener2);
        Registration subscription3 = testSubject.subscribe(listener3);
        testSubject.publish(newEvent());
        subscription1.cancel();
        testSubject.publish(newEvent());
        subscription2.cancel();
        subscription3.cancel();
        // unsubscribe a non-subscribed listener should not fail
        subscription3.cancel();
        testSubject.publish(newEvent());

        verify(listener1, times(2)).accept(anyList());
        verify(listener2, times(2)).accept(anyList());
        verify(listener3, times(2)).accept(anyList());
    }

    private EventMessage<Object> newEvent() {
        return new GenericEventMessage<>(new MessageType("event"), new Object());
    }
}
