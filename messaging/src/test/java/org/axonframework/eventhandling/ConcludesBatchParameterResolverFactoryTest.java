/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.*;

import java.lang.reflect.Method;
import java.util.List;

import static junit.framework.TestCase.*;
import static org.axonframework.utils.EventTestUtils.createEvents;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;

public class ConcludesBatchParameterResolverFactoryTest {

    private ConcludesBatchParameterResolverFactory subject = new ConcludesBatchParameterResolverFactory();

    @Test
    public void testCreateInstance() throws Exception {
        Method method = getClass().getDeclaredMethod("handle", String.class, Boolean.class);
        assertSame(subject.getResolver(), subject.createInstance(method, method.getParameters(), 1));
        method = getClass().getDeclaredMethod("handlePrimitive", String.class, boolean.class);
        assertSame(subject.getResolver(), subject.createInstance(method, method.getParameters(), 1));
    }

    @Test
    public void testOnlyMatchesEventMessages() {
        assertTrue(subject.matches(asEventMessage("testEvent")));
        assertFalse(subject.matches(new GenericCommandMessage<>("testCommand")));
    }

    @Test
    public void testResolvesToTrueWithoutUnitOfWork() {
        assertTrue(subject.resolveParameterValue(asEventMessage("testEvent")));
    }

    @Test
    public void testResolvesToTrueWithRegularUnitOfWork() {
        EventMessage<?> event = asEventMessage("testEvent");
        DefaultUnitOfWork.startAndGet(event).execute(() -> assertTrue(subject.resolveParameterValue(event)));
    }

    @Test
    public void testResolvesToFalseWithBatchingUnitOfWorkIfMessageIsNotLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new BatchingUnitOfWork<>(events).execute(() -> assertFalse(subject.resolveParameterValue(events.get(0))));
    }

    @Test
    public void testResolvesToFalseWithBatchingUnitOfWorkIfMessageIsLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new BatchingUnitOfWork<>(events).execute(() -> assertTrue(subject.resolveParameterValue(events.get(4))));
    }

    @SuppressWarnings("unused")
    private void handle(String eventPayload, @ConcludesBatch Boolean concludesBatch) {
    }

    @SuppressWarnings("unused")
    private void handlePrimitive(String eventPayload, @ConcludesBatch boolean concludesBatch) {
    }

}