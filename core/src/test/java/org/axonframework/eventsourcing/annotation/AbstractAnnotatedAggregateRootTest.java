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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.common.configuration.AnnotationConfiguration;
import org.axonframework.domain.Message;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;

import java.lang.annotation.Annotation;
import java.util.UUID;
import javax.persistence.Id;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedAggregateRootTest {

    private SimpleAggregateRoot testSubject;

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        assertEquals(1, testSubject.getUncommittedEventCount());
        // this proves that a newly added entity is also notified of an event
        assertEquals(1, testSubject.getEntity().invocationCount);

        testSubject.doSomething();

        assertEquals(2, testSubject.invocationCount);
        assertEquals(2, testSubject.getEntity().invocationCount);
    }

    @Test
    public void testInitializeWithIdentifier() {
        testSubject = new SimpleAggregateRoot(UUID.randomUUID());
        assertEquals(0, testSubject.getUncommittedEventCount());
    }

    @Test
    public void testHandleEventWithCustomParameter() {

        final SomeResource resource = mock(SomeResource.class);
        AnnotationConfiguration.configure(CustomParameterHandlerAggregateRoot.class)
                               .useParameterResolverFactory(new SimpleParameterResolverFactory(resource));
        try {
            testSubject = new CustomParameterHandlerAggregateRoot();
            assertEquals(1, testSubject.getUncommittedEventCount());
            verify(resource).registerInvocation();
        } finally {
            AnnotationConfiguration.reset(CustomParameterHandlerAggregateRoot.class);
        }
    }

    @Test
    public void testIdentifierInitialization_LateInitialization() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());
    }

    @Test
    public void testIdentifierInitialization_JavaxPersistenceId() {
        JavaxPersistenceIdIdentifiedAggregate aggregate = new JavaxPersistenceIdIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());
    }

    private static class LateIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String aggregateIdentifier;

        private LateIdentifiedAggregate() {
            apply(new StubDomainEvent());
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
        }
    }

    private static class JavaxPersistenceIdIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @Id
        private String aggregateIdentifier;

        private JavaxPersistenceIdIdentifiedAggregate() {
            apply(new StubDomainEvent());
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
        }
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private int invocationCount;
        @EventSourcedMember
        private SimpleEntity entity;
        @AggregateIdentifier
        private final UUID identifier;

        private SimpleAggregateRoot() {
            identifier = UUID.randomUUID();
            apply(new StubDomainEvent());
        }

        private SimpleAggregateRoot(UUID identifier) {
            this.identifier = identifier;
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
            if (entity == null) {
                entity = new SimpleEntity();
            }
        }

        public SimpleEntity getEntity() {
            return entity;
        }

        public void doSomething() {
            apply(new StubDomainEvent());
        }
    }


    private static class CustomParameterHandlerAggregateRoot extends SimpleAggregateRoot {

        private CustomParameterHandlerAggregateRoot() {
            super();
        }

        private CustomParameterHandlerAggregateRoot(UUID identifier) {
            super(identifier);
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event, SomeResource resource) {
            super.myEventHandlerMethod(event);
            resource.registerInvocation();
        }

        public void doSomething() {
            apply(new StubDomainEvent());
        }
    }

    interface SomeResource {

        void registerInvocation();
    }

    private static class SimpleEntity extends AbstractAnnotatedEntity {

        private int invocationCount;

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
        }
    }

    private static class SimpleParameterResolverFactory implements ParameterResolverFactory, ParameterResolver<Object> {

        private final Object resource;

        public SimpleParameterResolverFactory(Object resource) {
            this.resource = resource;
        }

        @Override
        public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                                Annotation[] parameterAnnotations) {
            if (parameterType.isInstance(resource)) {
                return this;
            }
            return null;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return resource;
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }
}
