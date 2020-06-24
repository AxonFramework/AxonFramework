/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.eventhandling.ResetNotSupportedException;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.saga.metamodel.AnnotationSagaMetaModelFactory;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.axonframework.modelling.saga.SagaLifecycle.removeAssociationWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotatedSaga}.
 *
 * @author Allard Buijze
 * @author Sofia Guy Ang
 */
class AnnotatedSagaTest {

    private StubAnnotatedSaga testSaga;
    private AnnotatedSaga<StubAnnotatedSaga> testSubject;

    @BeforeEach
    void setUp() {
        testSaga = new StubAnnotatedSaga();
        testSubject = new AnnotatedSaga<>(
                "id", Collections.emptySet(), testSaga,
                new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class)
        );
    }

    @Test
    void testInvokeSaga() {
        testSubject.doAssociateWith(new AssociationValue("propertyName", "id"));
        testSubject.handle(new GenericEventMessage<>(new RegularEvent("id")));
        testSubject.handle(new GenericEventMessage<>(new RegularEvent("wrongId")));
        testSubject.handle(new GenericEventMessage<>(new Object()));

        assertEquals(1, testSaga.invocationCount);
    }

    @Test
    void testInvokeSaga_AssociationPropertyNotExistingInPayload() {
        assertThrows(
                AxonConfigurationException.class,
                () -> new AnnotationSagaMetaModelFactory().modelOf(SagaAssociationPropertyNotExistingInPayload.class)
        );
    }

    @Test
    void testInvokeSaga_MetaDataAssociationResolver() {
        testSubject.doAssociateWith(new AssociationValue("propertyName", "id"));
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("propertyName", "id");
        testSubject.handle(new GenericEventMessage<>(new EventWithoutProperties(), new MetaData(metaData)));
        testSubject.handle(new GenericEventMessage<>(new EventWithoutProperties()));

        assertEquals(1, testSaga.invocationCount);
    }

    @Test
    void testInvokeSaga_ResolverWithoutNoArgConstructor() {
        assertThrows(
                AxonConfigurationException.class,
                () -> new AnnotationSagaMetaModelFactory().modelOf(SagaUsingResolverWithoutNoArgConstructor.class)
        );
    }

    @Test
    void testEndedAfterInvocation_BeanProperty() {
        testSubject.doAssociateWith(new AssociationValue("propertyName", "id"));
        testSubject.handle(new GenericEventMessage<>(new RegularEvent("id")));
        testSubject.handle(new GenericEventMessage<>(new Object()));
        testSubject.handle(new GenericEventMessage<>(new SagaEndEvent("id")));

        assertEquals(2, testSaga.invocationCount);
        assertFalse(testSubject.isActive());
    }

    @Test
    void testEndedAfterInvocation_WhenAssociationIsRemoved() {
        StubAnnotatedSaga testSaga = new StubAnnotatedSagaWithExplicitAssociationRemoval();
        AnnotatedSaga<StubAnnotatedSaga> testSubject = new AnnotatedSaga<>(
                "id", Collections.emptySet(), testSaga,
                new AnnotationSagaMetaModelFactory().modelOf(StubAnnotatedSaga.class)
        );

        testSubject.doAssociateWith(new AssociationValue("propertyName", "id"));
        testSubject.handle(new GenericEventMessage<>(new RegularEvent("id")));
        testSubject.handle(new GenericEventMessage<>(new Object()));
        testSubject.handle(new GenericEventMessage<>(new SagaEndEvent("id")));

        assertEquals(2, testSaga.invocationCount);
        assertFalse(testSubject.isActive());
    }

    @Test
    void testEndedAfterInvocation_UniformAccessPrinciple() {
        testSubject.doAssociateWith(new AssociationValue("propertyName", "id"));
        testSubject.handle(new GenericEventMessage<>(new UniformAccessEvent("id")));
        testSubject.handle(new GenericEventMessage<>(new Object()));
        testSubject.handle(new GenericEventMessage<>(new SagaEndEvent("id")));

        assertEquals(2, testSaga.invocationCount);
        assertFalse(testSubject.isActive());
    }

    @Test
    void testPrepareResetThrowsResetNotSupportedException() {
        AnnotatedSaga<StubAnnotatedSaga> spiedTestSubject = spy(testSubject);

        assertThrows(ResetNotSupportedException.class, spiedTestSubject::prepareReset);

        verify(spiedTestSubject).prepareReset(null);
    }

    @Test
    void testPrepareResetWithResetContextThrowsResetNotSupportedException() {
        assertThrows(ResetNotSupportedException.class, () -> testSubject.prepareReset("some-reset-context"));
    }

    @SuppressWarnings("unused")
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

        @SuppressWarnings("unused")
        @SagaEventHandler(associationProperty = "propertyName")
        public void handleStubDomainEvent(EventWithoutProperties event) {
        }
    }

    private static class SagaUsingResolverWithoutNoArgConstructor {

        @SuppressWarnings("unused")
        @SagaEventHandler(
                associationProperty = "propertyName",
                associationResolver = OneArgConstructorAssociationResolver.class
        )
        public void handleStubDomainEvent(EventWithoutProperties event) {
        }
    }

    private static class StubAnnotatedSagaWithExplicitAssociationRemoval extends StubAnnotatedSaga {

        @Override
        public void handleStubDomainEvent(SagaEndEvent event) {
            // Since this method overrides a handler, it doesn't need the annotations anymore
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

        @SuppressWarnings("unused")
        public String propertyName() {
            return propertyName;
        }
    }

    private static class EventWithoutProperties {

    }

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
