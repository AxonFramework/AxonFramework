/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.metamodel.DefaultSagaMetaModelFactory;
import org.junit.Test;

import java.util.Collections;

import static org.axonframework.eventhandling.saga.SagaLifecycle.removeAssociationWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTest {

    @Test
    public void testInvokeSaga() throws Exception {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new DefaultSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new RegularEvent("id")));
        s.handle(new GenericEventMessage<>(new RegularEvent("wrongId")));
        s.handle(new GenericEventMessage<>(new Object()));
        assertEquals(1, testSubject.invocationCount);
    }

    @Test
    public void testEndedAfterInvocation_BeanProperty() throws Exception {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new DefaultSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new RegularEvent("id")));
        s.handle(new GenericEventMessage<>(new Object()));
        s.handle(new GenericEventMessage<>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(s.isActive());
    }

    @Test
    public void testEndedAfterInvocation_WhenAssociationIsRemoved() throws Exception {
        StubAnnotatedSaga testSubject = new StubAnnotatedSagaWithExplicitAssociationRemoval();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new DefaultSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new RegularEvent("id")));
        s.handle(new GenericEventMessage<>(new Object()));
        s.handle(new GenericEventMessage<>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(s.isActive());
    }

    @Test
    public void testEndedAfterInvocation_UniformAccessPrinciple() throws Exception {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new DefaultSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new UniformAccessEvent("id")));
        s.handle(new GenericEventMessage<>(new Object()));
        s.handle(new GenericEventMessage<>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(s.isActive());
    }

    private static class StubAnnotatedSaga {

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

    private static class StubAnnotatedSagaWithExplicitAssociationRemoval extends StubAnnotatedSaga {

        @Override
        public void handleStubDomainEvent(SagaEndEvent event) {
            // since this method overrides a handler, it doesn't need the annotations anymore
            super.handleStubDomainEvent(event);
            removeAssociationWith("propertyName", event.getPropertyName());
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
