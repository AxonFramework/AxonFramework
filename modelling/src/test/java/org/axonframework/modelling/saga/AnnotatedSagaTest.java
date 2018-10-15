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

package org.axonframework.modelling.saga;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.modelling.saga.metamodel.AnnotationSagaMetaModelFactory;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.axonframework.modelling.saga.SagaLifecycle.removeAssociationWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Allard Buijze
 * @author Sofia Guy Ang
 */
public class AnnotatedSagaTest {

    @Test
    public void testInvokeSaga() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new RegularEvent("id")));
        s.handle(new GenericEventMessage<>(new RegularEvent("wrongId")));
        s.handle(new GenericEventMessage<>(new Object()));
        assertEquals(1, testSubject.invocationCount);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testInvokeSaga_AssociationPropertyNotExistingInPayload() {
        SagaAssociationPropertyNotExistingInPayload testSubject = new SagaAssociationPropertyNotExistingInPayload();
        AnnotatedSaga<SagaAssociationPropertyNotExistingInPayload> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                                           new AnnotationSagaMetaModelFactory().modelOf(SagaAssociationPropertyNotExistingInPayload.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new EventWithoutProperties()));
    }

    @Test
    public void testInvokeSaga_MetaDataAssociationResolver() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("propertyName", "id");
        s.handle(new GenericEventMessage<>(new EventWithoutProperties(), new MetaData(metaData)));
        s.handle(new GenericEventMessage<>(new EventWithoutProperties()));
        assertEquals(1, testSubject.invocationCount);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testInvokeSaga_ResolverWithoutNoArgConstructor() {
        SagaUsingResolverWithoutNoArgConstructor testSubject = new SagaUsingResolverWithoutNoArgConstructor();
        AnnotatedSaga<SagaUsingResolverWithoutNoArgConstructor> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                                        new AnnotationSagaMetaModelFactory().modelOf(SagaUsingResolverWithoutNoArgConstructor.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("propertyName", "id");
        s.handle(new GenericEventMessage<>(new EventWithoutProperties(), new MetaData(metaData)));
    }

    @Test
    public void testEndedAfterInvocation_BeanProperty() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new RegularEvent("id")));
        s.handle(new GenericEventMessage<>(new Object()));
        s.handle(new GenericEventMessage<>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(s.isActive());
    }

    @Test
    public void testEndedAfterInvocation_WhenAssociationIsRemoved() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSagaWithExplicitAssociationRemoval();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
        s.doAssociateWith(new AssociationValue("propertyName", "id"));
        s.handle(new GenericEventMessage<>(new RegularEvent("id")));
        s.handle(new GenericEventMessage<>(new Object()));
        s.handle(new GenericEventMessage<>(new SagaEndEvent("id")));
        assertEquals(2, testSubject.invocationCount);
        assertFalse(s.isActive());
    }

    @Test
    public void testEndedAfterInvocation_UniformAccessPrinciple() {
        StubAnnotatedSaga testSubject = new StubAnnotatedSaga();
        AnnotatedSaga<StubAnnotatedSaga> s = new AnnotatedSaga<>("id", Collections.emptySet(), testSubject,
                                                                 new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class));
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

        @SagaEventHandler(associationProperty = "propertyName", associationResolver = MetaDataAssociationResolver.class)
        public void handleStubDomainEvent(EventWithoutProperties event) {
            invocationCount++;
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "propertyName")
        public void handleStubDomainEvent(SagaEndEvent event) {
            invocationCount++;
        }
    }

    private static class SagaAssociationPropertyNotExistingInPayload {
        @SagaEventHandler(associationProperty = "propertyName", associationResolver = PayloadAssociationResolver.class)
        public void handleStubDomainEvent(EventWithoutProperties event) {}
    }

    private static class SagaUsingResolverWithoutNoArgConstructor {
        @SagaEventHandler(associationProperty = "propertyName", associationResolver = OneArgConstructorAssociationResolver.class)
        public void handleStubDomainEvent(EventWithoutProperties event) {}
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

    private static class EventWithoutProperties {}

    private static class SagaEndEvent extends RegularEvent {

        public SagaEndEvent(String propertyName) {
            super(propertyName);
        }
    }

    private static class OneArgConstructorAssociationResolver implements AssociationResolver {

        String someField;

        public OneArgConstructorAssociationResolver(String someField) {
            this.someField = someField;
        }

        @Override
        public <T> void validate(String associationPropertyName, MessageHandlingMember<T> handler) {

        }

        @Override
        public <T> Object resolve(String associationPropertyName, EventMessage<?> message,
                                  MessageHandlingMember<T> handler) {
            return null;
        }
    }
}
