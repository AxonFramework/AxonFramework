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

package org.axonframework.saga.annotation;

import org.axonframework.domain.GenericEventMessage;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedSagaTest {

    @Test
    public void testInvokeSaga() throws Exception {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        testSubject.handle(new GenericEventMessage<RegularEvent>(new RegularEvent("id")));
        testSubject.handle(new GenericEventMessage<Object>(new Object()));
        assertEquals(1, testSubject.invocationCount);
    }

    @Test
    public void testSerializeAndInvokeSaga() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(new StubAnnotatedSaga());
        StubAnnotatedSaga testSubject = (StubAnnotatedSaga) new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))
                .readObject();
        testSubject.handle(new GenericEventMessage<RegularEvent>(new RegularEvent("id")));
        testSubject.handle(new GenericEventMessage<Object>(new Object()));
        assertEquals(1, testSubject.invocationCount);
    }

    @Test
    public void testEndedAfterInvocation_BeanProperty() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        testSubject.handle(new GenericEventMessage<RegularEvent>(new RegularEvent("id")));
        testSubject.handle(new GenericEventMessage<Object>(new Object()));
        testSubject.handle(new GenericEventMessage<SagaEndEvent>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(testSubject.isActive());
    }

    @Test
    public void testEndedAfterInvocation_UniformAccessPrinciple() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        testSubject.handle(new GenericEventMessage<UniformAccessEvent>(new UniformAccessEvent("id")));
        testSubject.handle(new GenericEventMessage<Object>(new Object()));
        testSubject.handle(new GenericEventMessage<SagaEndEvent>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(testSubject.isActive());
    }

    private static class StubAnnotatedSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -3224806999195676097L;
        private int invocationCount = 0;

        @SagaEventHandler(associationProperty = "propertyName")
        public void handleStubDomainEvent(RegularEvent event) {
            invocationCount++;
        }

        @SagaEventHandler(associationProperty = "propertyName")
        public void handleStubDomainEvent(UniformAccessEvent event) {
            invocationCount++;
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "propertyName")
        public void handleStubDomainEvent(SagaEndEvent event) {
            invocationCount++;
        }
    }

    private static class RegularEvent {

        private final String propertyName;

        public RegularEvent(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }

    private static class UniformAccessEvent {

        private final String propertyName;

        public UniformAccessEvent(String propertyName) {
            this.propertyName = propertyName;
        }

        public String propertyName() {
            return propertyName;
        }
    }

    private static class SagaEndEvent extends RegularEvent {

        public SagaEndEvent(String propertyName) {
            super(propertyName);
        }
    }
}
