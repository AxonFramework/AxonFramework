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

package org.axonframework.contextsupport.spring;

import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventRegistrationCallback;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.CachingEventSourcingRepository;
import org.axonframework.eventsourcing.EventCountSnapshotterTrigger;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.repository.LockingStrategy;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class RepositoryBeanDefinitionParserTest {

    /**
     * Mock {@link EventSourcedAggregateRoot} instance for the test.
     *
     * @author Ben Z. Tels
     */
    public static class EventSourcedAggregateRootMock implements EventSourcedAggregateRoot {

        @AggregateIdentifier
        private String id;

        /**
         *
         */
        public EventSourcedAggregateRootMock() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getIdentifier() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void commitEvents() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getUncommittedEventCount() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DomainEventStream getUncommittedEvents() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDeleted() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public void addEventRegistrationCallback(EventRegistrationCallback eventRegistrationCallback) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Long getVersion() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void initializeState(DomainEventStream domainEventStream) {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testRepository() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("testRepository");
        assertNotNull("BeanDefinition not created", beanDefinition);

        assertEquals("Wrong number of arguments", 2, beanDefinition.getConstructorArgumentValues().getArgumentCount());
        ValueHolder firstArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(0,
                                                                                                   AggregateFactory.class);
        assertNotNull("First argument is wrong", firstArgument);
        assertEquals("First argument is wrong", GenericAggregateFactory.class, firstArgument.getValue().getClass());
        assertEquals("First argument is wrong",
                     EventSourcedAggregateRootMock.class.getSimpleName(),
                     ((GenericAggregateFactory) firstArgument.getValue()).getTypeIdentifier());
        ValueHolder secondArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(1,
                                                                                                    LockingStrategy.class);
        assertNotNull("Second argument is wrong", secondArgument);
        assertEquals("Second argument is wrong", LockingStrategy.PESSIMISTIC, secondArgument.getValue());

        PropertyValue eventBusPropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventBus");
        assertNotNull("Property missing", eventBusPropertyValue);
        RuntimeBeanReference eventBusReference = (RuntimeBeanReference) eventBusPropertyValue.getValue();
        assertEquals("Wrong reference", "eventBus", eventBusReference.getBeanName());

        PropertyValue eventStorePropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventStore");
        assertNotNull("Property missing", eventStorePropertyValue);
        RuntimeBeanReference eventStoreReference = (RuntimeBeanReference) eventStorePropertyValue.getValue();
        assertEquals("Wrong reference", "eventStore", eventStoreReference.getBeanName());

        PropertyValue conflictResolverPropertyValue = beanDefinition.getPropertyValues().getPropertyValue(
                "conflictResolver");
        assertNotNull("Property missing", conflictResolverPropertyValue);
        RuntimeBeanReference conflictResolverReference = (RuntimeBeanReference) conflictResolverPropertyValue
                .getValue();
        assertEquals("Wrong reference", "conflictResolver", conflictResolverReference.getBeanName());

        PropertyValue eventStreamDecoratorsProperty = beanDefinition.getPropertyValues()
                                                                    .getPropertyValue("eventStreamDecorators");
        assertNotNull("Property missing", eventStreamDecoratorsProperty);
        List decorators = (List) eventStreamDecoratorsProperty.getValue();
        assertEquals("Wrong number of decorators", 1, decorators.size());
        PropertyValue snapshotterTrigger = beanDefinition.getPropertyValues()
                                                         .getPropertyValue("snapshotterTrigger");
        assertNotNull("Property missing", snapshotterTrigger);

        @SuppressWarnings("unchecked")
        EventSourcingRepository<EventSourcedAggregateRootMock> repository =
                beanFactory.getBean("testRepository", EventSourcingRepository.class);
        assertNotNull(repository);
    }

    @Test
    public void testCacheRepository() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("testCacheRepository");
        assertNotNull("BeanDefinition not created", beanDefinition);

        ValueHolder firstArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(0,
                                                                                                   AggregateFactory.class);
        assertNotNull("First argument is wrong", firstArgument);
        assertEquals("First argument is wrong", GenericAggregateFactory.class, firstArgument.getValue().getClass());
        assertEquals("First argument is wrong",
                     EventSourcedAggregateRootMock.class.getSimpleName(),
                     ((GenericAggregateFactory) firstArgument.getValue()).getTypeIdentifier());

        PropertyValue eventBusPropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventBus");
        assertNotNull("Property missing", eventBusPropertyValue);
        RuntimeBeanReference eventBusReference = (RuntimeBeanReference) eventBusPropertyValue.getValue();
        assertEquals("Wrong reference", "eventBus", eventBusReference.getBeanName());

        PropertyValue eventStorePropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventStore");
        assertNotNull("Property missing", eventStorePropertyValue);
        RuntimeBeanReference eventStoreReference = (RuntimeBeanReference) eventStorePropertyValue.getValue();
        assertEquals("Wrong reference", "eventStore", eventStoreReference.getBeanName());

        PropertyValue conflictResolverPropertyValue = beanDefinition.getPropertyValues().getPropertyValue(
                "conflictResolver");
        assertNotNull("Property missing", conflictResolverPropertyValue);
        RuntimeBeanReference conflictResolverReference = (RuntimeBeanReference) conflictResolverPropertyValue
                .getValue();
        assertEquals("Wrong reference", "conflictResolver", conflictResolverReference.getBeanName());

        PropertyValue cacheRefProperty = beanDefinition.getPropertyValues()
                                                       .getPropertyValue("cache");
        assertNotNull("Property missing", cacheRefProperty);

        PropertyValue snapshotterTrigger = beanDefinition.getPropertyValues()
                                                         .getPropertyValue("snapshotterTrigger");
        assertNotNull("Property missing", snapshotterTrigger);
        BeanDefinition decorators = (BeanDefinition) snapshotterTrigger.getValue();
        assertNotNull("Property 'aggregateCache' not set",
                      decorators.getPropertyValues().getPropertyValue("aggregateCache"));

        @SuppressWarnings("unchecked")
        EventSourcingRepository<EventSourcedAggregateRootMock> repository =
                beanFactory.getBean("testCacheRepository", CachingEventSourcingRepository.class);
        assertNotNull(repository);
    }

    @Test
    public void defaultStrategyRepository() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("defaultStrategyRepository");
        assertNotNull("BeanDefinition not created", beanDefinition);

        assertEquals("Wrong number of arguments", 2, beanDefinition.getConstructorArgumentValues().getArgumentCount());
        ValueHolder firstArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(0,
                                                                                                   AggregateFactory.class);
        assertNotNull("First argument is wrong", firstArgument);
        assertEquals("First argument is wrong", GenericAggregateFactory.class, firstArgument.getValue().getClass());
        assertEquals("First argument is wrong",
                     EventSourcedAggregateRootMock.class.getSimpleName(),
                     ((GenericAggregateFactory) firstArgument.getValue()).getTypeIdentifier());

        ValueHolder secondArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(1,
                                                                                                    LockingStrategy.class);
        assertNotNull("Second argument is wrong", secondArgument);
        assertEquals("Second argument is wrong", LockingStrategy.PESSIMISTIC, secondArgument.getValue());

        PropertyValue eventBusPropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventBus");
        assertNotNull("Property missing", eventBusPropertyValue);
        RuntimeBeanReference eventBusReference = (RuntimeBeanReference) eventBusPropertyValue.getValue();
        assertEquals("Wrong reference", "eventBus", eventBusReference.getBeanName());

        PropertyValue eventStorePropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventStore");
        assertNotNull("Property missing", eventStorePropertyValue);
        RuntimeBeanReference eventStoreReference = (RuntimeBeanReference) eventStorePropertyValue.getValue();
        assertEquals("Wrong reference", "eventStore", eventStoreReference.getBeanName());

        PropertyValue conflictResolverPropertyValue = beanDefinition.getPropertyValues().getPropertyValue(
                "conflictResolver");
        assertNotNull("Property missing", conflictResolverPropertyValue);
        RuntimeBeanReference conflictResolverReference = (RuntimeBeanReference) conflictResolverPropertyValue
                .getValue();
        assertEquals("Wrong reference", "conflictResolver", conflictResolverReference.getBeanName());

        @SuppressWarnings("unchecked")
        EventSourcingRepository<EventSourcedAggregateRootMock> repository = beanFactory.getBean(
                "defaultStrategyRepository", EventSourcingRepository.class);
        assertNotNull(repository);
    }

    @Test
    public void testRepositoryCacheSetInSnapshotTrigger() {
        EventCountSnapshotterTrigger snapshotTrigger = (EventCountSnapshotterTrigger) beanFactory.getBean(
                "snapshotterTrigger");
        assertNotNull(snapshotTrigger);
//        snapshotTrigger.
    }
}
