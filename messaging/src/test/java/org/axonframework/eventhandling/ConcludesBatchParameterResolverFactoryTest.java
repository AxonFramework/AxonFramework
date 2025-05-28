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

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.LegacyBatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.List;

import static org.axonframework.eventhandling.EventTestUtils.asEventMessage;
import static org.axonframework.messaging.StubProcessingContext.forMessage;
import static org.axonframework.utils.EventTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.*;

class ConcludesBatchParameterResolverFactoryTest {

    private final ConcludesBatchParameterResolverFactory testSubject = new ConcludesBatchParameterResolverFactory();

    @Test
    void createInstance() throws Exception {
        Method method = getClass().getDeclaredMethod("handle", String.class, Boolean.class);
        assertSame(testSubject.getResolver(), testSubject.createInstance(method, method.getParameters(), 1));
        method = getClass().getDeclaredMethod("handlePrimitive", String.class, boolean.class);
        assertSame(testSubject.getResolver(), testSubject.createInstance(method, method.getParameters(), 1));
    }

    @Test
    void onlyMatchesEventMessages() {
        assertTrue(testSubject.matches(forMessage(asEventMessage("testEvent"))));
        assertFalse(testSubject.matches(forMessage(new GenericCommandMessage<>(
                new MessageType("command"), "testCommand")
        )));
    }

    @Test
    void resolvesToTrueWithoutUnitOfWork() {
        assertEquals(true,
                     testSubject.resolveParameterValue(forMessage(asEventMessage("testEvent"))));
    }

    @Test
    void resolvesToTrueWithRegularUnitOfWork() {
        EventMessage<?> event = asEventMessage("testEvent");
        LegacyDefaultUnitOfWork.startAndGet(event)
                               .execute((ctx) -> assertEquals(Boolean.TRUE, testSubject.resolveParameterValue(ctx)));
    }

    @Test
    void resolvesToFalseWithBatchingUnitOfWorkIfMessageIsNotLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new LegacyBatchingUnitOfWork<>(events)
                .execute((ctx) -> {
                    ProcessingContext event0Context = forMessage(events.get(0));
                    assertFalse(testSubject.resolveParameterValue(event0Context));
                });
    }

    @Test
    void resolvesToTrueWithBatchingUnitOfWorkIfMessageIsLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new LegacyBatchingUnitOfWork<>(events)
                .execute((ctx) -> {
                    ProcessingContext lastEventContext = forMessage(events.get(4));
                    assertTrue(testSubject.resolveParameterValue(lastEventContext));
                });
    }

    @SuppressWarnings("unused")
    private void handle(String eventPayload, @ConcludesBatch Boolean concludesBatch) {
    }

    @SuppressWarnings("unused")
    private void handlePrimitive(String eventPayload, @ConcludesBatch boolean concludesBatch) {
    }
}