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

package org.axonframework.spring.config.xml;

import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class RepositoryBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testRepository() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("testRepository");
        assertNotNull("BeanDefinition not created", beanDefinition);

        assertEquals("Wrong number of arguments", 3, beanDefinition.getConstructorArgumentValues().getArgumentCount());
        ValueHolder firstArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(0,
                                                                                                   AggregateFactory.class);
        assertNotNull("First argument is wrong", firstArgument);
        assertEquals("First argument is wrong", GenericAggregateFactory.class,
                     ((GenericBeanDefinition) firstArgument.getValue()).getBeanClass());
        assertEquals("First argument is wrong", EventSourcedAggregateRootMock.class.getName(),
                     ((GenericBeanDefinition) firstArgument.getValue()).getConstructorArgumentValues().getIndexedArgumentValue(0, String.class).getValue());
        ValueHolder secondArgument = beanDefinition.getConstructorArgumentValues()
                                                   .getArgumentValue(1, BeanDefinition.class);
        assertNotNull("Second argument is wrong", secondArgument);
        assertTrue("Second argument is wrong", secondArgument.getValue() instanceof RuntimeBeanReference);
        assertEquals("Second argument is wrong", "eventStore",
                     ((RuntimeBeanReference) secondArgument.getValue()).getBeanName());
        ValueHolder thirdArgument = beanDefinition.getConstructorArgumentValues()
                                                   .getArgumentValue(2, BeanDefinition.class);
        assertNotNull("Third argument is wrong", thirdArgument);
        assertTrue("Third argument is wrong", thirdArgument.getValue() instanceof BeanDefinition);
        assertEquals("Third argument is wrong",
                     PessimisticLockFactory.class.getName(),
                     ((BeanDefinition) thirdArgument.getValue()).getBeanClassName());

        PropertyValue eventBusPropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventBus");
        assertNotNull("Property missing", eventBusPropertyValue);
        RuntimeBeanReference eventBusReference = (RuntimeBeanReference) eventBusPropertyValue.getValue();
        assertEquals("Wrong reference", "eventBus", eventBusReference.getBeanName());

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
        assertEquals("First argument is wrong", RuntimeBeanReference.class, firstArgument.getValue().getClass());
        assertEquals("First argument is wrong",
                     "mockFactory", ((RuntimeBeanReference) firstArgument.getValue()).getBeanName());

        ValueHolder secondArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(1, EventStore.class);
        assertNotNull("Second argument is wrong", secondArgument);
        assertEquals("Second argument is wrong", RuntimeBeanReference.class, secondArgument.getValue().getClass());
        assertEquals("Wrong reference", "eventStore", ((RuntimeBeanReference) secondArgument.getValue()).getBeanName());

        ValueHolder thirdArgument = beanDefinition.getConstructorArgumentValues()
                                                   .getArgumentValue(2, BeanReference.class);
        assertNotNull("Lock factory reference is not resolved correctly", thirdArgument);
        assertEquals("nullLockFactory", ((BeanReference) thirdArgument.getValue()).getBeanName());

        PropertyValue eventBusPropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventBus");
        assertNotNull("Property missing", eventBusPropertyValue);
        RuntimeBeanReference eventBusReference = (RuntimeBeanReference) eventBusPropertyValue.getValue();
        assertEquals("Wrong reference", "eventBus", eventBusReference.getBeanName());


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
        assertEquals("First argument is wrong", GenericAggregateFactory.class,
                     ((GenericBeanDefinition) firstArgument.getValue()).getBeanClass());
        assertEquals("First argument is wrong", EventSourcedAggregateRootMock.class.getName(),
                     ((GenericBeanDefinition) firstArgument.getValue()).getConstructorArgumentValues().getIndexedArgumentValue(0, String.class).getValue());

        ValueHolder secondArgument = beanDefinition.getConstructorArgumentValues().getArgumentValue(1, EventStore.class);
        assertNotNull("Second argument is wrong", secondArgument);
        assertEquals("Second argument is wrong", RuntimeBeanReference.class, secondArgument.getValue().getClass());
        assertEquals("Wrong reference", "eventStore", ((RuntimeBeanReference)secondArgument.getValue()).getBeanName());

        PropertyValue eventBusPropertyValue = beanDefinition.getPropertyValues().getPropertyValue("eventBus");
        assertNotNull("Property missing", eventBusPropertyValue);
        RuntimeBeanReference eventBusReference = (RuntimeBeanReference) eventBusPropertyValue.getValue();
        assertEquals("Wrong reference", "eventBus", eventBusReference.getBeanName());

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

    public static class EventSourcedAggregateRootMock {

        @AggregateIdentifier
        private String id;

        /**
         *
         */
        public EventSourcedAggregateRootMock() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

    }
}
