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

package org.axonframework.unitofwork.nesting;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleListenerPublishingEventTest {

    private EventBus eventBus;
    private EventListener eventListener;

    @Before
    public void setUp() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        eventBus = new SimpleEventBus();
        eventListener = mock(EventListener.class);
        eventBus.subscribe(eventListener);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testEventPublishedByListenerIsHandled() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        GenericEventMessage<Object> event = new GenericEventMessage<Object>("First",
                                                                            Collections.<String, Object>singletonMap(
                                                                                    "continue",
                                                                                    true));
        uow.publishEvent(event,
                         eventBus);
        verify(eventListener, never()).handle(isA(EventMessage.class));
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                CurrentUnitOfWork.get().publishEvent(new GenericEventMessage<String>("Second"), eventBus);
                return null;
            }
        }).when(eventListener).handle(event);
        uow.commit();
        verify(eventListener, times(2)).handle(isA(EventMessage.class));
    }
}
