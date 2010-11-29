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

package org.axonframework.saga.annotation;

import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedSagaTest {

    @Test
    public void testInvokeSaga() throws Exception {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        testSubject.handle(new StubDomainEvent());
        testSubject.handle(mock(Event.class));
        assertEquals(1, testSubject.invocationCount);
    }

    @Test
    public void testSerializeAndInvokeSaga() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(new StubAnnotatedSaga());
        StubAnnotatedSaga testSubject = (StubAnnotatedSaga) new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))
                .readObject();
        testSubject.handle(new StubDomainEvent());
        testSubject.handle(mock(Event.class));
        assertEquals(1, testSubject.invocationCount);
    }

    private static class StubAnnotatedSaga extends AbstractAnnotatedSaga {

        private int invocationCount = 0;

        @SagaEventHandler(associationProperty = "aggregateIdentifier")
        public void handleStubDomainEvent(StubDomainEvent event) {
            invocationCount++;
        }

    }
}
