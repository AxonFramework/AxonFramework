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
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Allard Buijze
 */
public class SimpleListenerPublishingEventTest {

    private EventBus eventBus;
    private Cluster cluster;

    @Before
    public void setUp() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        eventBus = new SimpleEventBus();
        cluster = mock(Cluster.class);
        eventBus.subscribe(cluster);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testEventPublishedByListenerIsHandled() {
        GenericEventMessage<Object> event = new GenericEventMessage<>("First",
                                                                      Collections.<String, Object>singletonMap(
                                                                              "continue",
                                                                              true));
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(event);
        eventBus.publish(event);
        verify(cluster, never()).handle(Matchers.<EventMessage[]>anyVararg());
        verify(cluster, never()).handle(Matchers.<List<EventMessage<?>>>any());
        doAnswer(invocation -> {
            eventBus.publish(new GenericEventMessage<>("Second"));
            return null;
        }).when(cluster).handle(Collections.singletonList(event));
        uow.commit();
        verify(cluster, times(2)).handle(Matchers.<List<EventMessage<?>>>any());
    }
}
