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

import org.axonframework.eventstore.jpa.JpaEventStore;
import org.axonframework.serializer.Serializer;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.SimpleUpcasterChain;
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
public class JpaEventStoreBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void jpaEventStore() {
        BeanDefinition definition = beanFactory.getBeanDefinition("eventStore");
        assertNotNull("BeanDefinition not created", definition);
        assertEquals("Wrong bean class", JpaEventStore.class.getName(), definition.getBeanClassName());
        ValueHolder reference = definition.getConstructorArgumentValues().getArgumentValue(1, Serializer.class);
        assertNotNull("Event serializer reference is wrong", reference);
        RuntimeBeanReference beanReference = (RuntimeBeanReference) reference.getValue();
        assertEquals("Event serializer reference is wrong", "eventSerializer", beanReference.getBeanName());
        PropertyValue maxSnapshotsArchived = definition.getPropertyValues().getPropertyValue("maxSnapshotsArchived");
        assertNotNull("maxSnapshotsArchived is defined", maxSnapshotsArchived);
        assertEquals("maxSnapshotsArchived value", "2", maxSnapshotsArchived.getValue());
        PropertyValue batchSize = definition.getPropertyValues().getPropertyValue("batchSize");
        assertEquals("maxSnapshotsArchived value", "1000", batchSize.getValue());

        JpaEventStore jpaEventStore = beanFactory.getBean("eventStore", JpaEventStore.class);
        assertNotNull(jpaEventStore);
    }

    @Test
    public void jpaEventStore_withGivenUpcasterChainStrategy() {
        BeanDefinition definition = beanFactory.getBeanDefinition("eventStore2");
        PropertyValue upcasterChain = definition.getPropertyValues().getPropertyValue("upcasterChain");
        BeanDefinition upcasterChainDefinition = (BeanDefinition) upcasterChain.getValue();
        assertEquals(SimpleUpcasterChain.class.getName(), upcasterChainDefinition.getBeanClassName());
        assertEquals(2, upcasterChainDefinition.getConstructorArgumentValues().getArgumentCount());
        ValueHolder converterFactory = upcasterChainDefinition.getConstructorArgumentValues()
                                                              .getGenericArgumentValue(RuntimeBeanReference.class);
        ValueHolder upcasterList = upcasterChainDefinition.getConstructorArgumentValues()
                                                          .getIndexedArgumentValue(1, List.class);
        assertNotNull(upcasterList);
        assertEquals(1, ((List) upcasterList.getValue()).size());
        assertNotNull(converterFactory);
        assertEquals("converterFactory", ((RuntimeBeanReference) converterFactory.getValue()).getBeanName());

        assertNotNull(beanFactory.getBean("eventStore2"));
    }

    @Test
    public void jpaEventStore_withDefaultUpcasterChainStrategy() {
        BeanDefinition definition = beanFactory.getBeanDefinition("eventStore3");
        PropertyValue upcasterChain = definition.getPropertyValues().getPropertyValue("upcasterChain");
        BeanDefinition upcasterChainDefinition = (BeanDefinition) upcasterChain.getValue();
        assertEquals(LazyUpcasterChain.class.getName(), upcasterChainDefinition.getBeanClassName());
        assertEquals(2, upcasterChainDefinition.getConstructorArgumentValues().getArgumentCount());
        ValueHolder upcasterList = upcasterChainDefinition.getConstructorArgumentValues()
                                                          .getIndexedArgumentValue(1, List.class);
        assertNotNull(upcasterList);
        assertEquals(2, ((List) upcasterList.getValue()).size());

        assertNotNull(beanFactory.getBean("eventStore3"));
    }
}
