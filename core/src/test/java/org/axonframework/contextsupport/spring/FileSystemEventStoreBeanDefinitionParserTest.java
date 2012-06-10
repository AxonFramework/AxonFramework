/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.eventstore.fs.EventFileResolver;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.serializer.Serializer;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class FileSystemEventStoreBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void fileEventStore() {
        BeanDefinition definition = beanFactory.getBeanDefinition("fileEventStore");
        assertNotNull("BeanDefinition not created", definition);
        assertEquals("Wrong bean class", FileSystemEventStore.class.getName(), definition.getBeanClassName());
        assertNull("Entity manager should not have been defined", definition.getPropertyValues().getPropertyValue(
                "entityManager"));

        ValueHolder reference = definition.getConstructorArgumentValues().getArgumentValue(0, Serializer.class);
        assertNotNull("Event serializer reference is wrong", reference);
        RuntimeBeanReference beanReference = (RuntimeBeanReference) reference.getValue();
        assertEquals("Event serializer reference is wrong", "eventSerializer", beanReference.getBeanName());

        assertNotNull("File event resolver is missing",
                      definition.getConstructorArgumentValues().getArgumentValue(1, EventFileResolver.class));
        assertNotNull("UpcasterChain is missing",
                      definition.getPropertyValues().getPropertyValue("upcasterChain"));

        FileSystemEventStore fileEventStore = beanFactory.getBean("fileEventStore", FileSystemEventStore.class);
        assertNotNull(fileEventStore);
    }
}
