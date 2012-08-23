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

import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.disruptor.DisruptorConfiguration;
import org.axonframework.repository.Repository;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/disruptor-context.xml"})
public class DisruptorCommandBusBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Autowired
    private DisruptorCommandBus disruptorCommandBus;

    @Test
    public void testBeansAvailableInContext() {
        assertNotNull(beanFactory);
        assertNotNull(disruptorCommandBus);
        Object repo1 = beanFactory.getBean("myAggregateRepository");
        Object repo2 = beanFactory.getBean("myOtherAggregateRepository");
        assertTrue(repo1 instanceof Repository);
        assertTrue(repo2 instanceof Repository);
        assertNotSame(repo1, repo2);
    }

    @Test
    public void testBeanProperties() {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition)
                beanFactory.getBeanDefinition("disruptorCommandBus");
        // no setter injection
        assertEquals(0, beanDefinition.getPropertyValues().size());
        assertEquals(3, beanDefinition.getConstructorArgumentValues().getArgumentCount());
        assertEquals("stop", beanDefinition.getDestroyMethodName());
        final ConstructorArgumentValues.ValueHolder argumentValue =
                beanDefinition.getConstructorArgumentValues().getArgumentValue(2, DisruptorConfiguration.class);
        assertNotNull(argumentValue);
        BeanDefinition config = (BeanDefinition) argumentValue.getValue();
        assertEquals(0, config.getConstructorArgumentValues().getArgumentCount());
        for (String property : new String[]{"dispatchInterceptors", "publisherInterceptors", "invokerInterceptors"}) {
            final PropertyValue propertyValue = config.getPropertyValues().getPropertyValue(property);
            assertNotNull(property + " is unknown", propertyValue);
            List interceptors = (List) propertyValue.getValue();
            assertEquals(property + " has wrong value", 2, interceptors.size());
        }
    }
}
