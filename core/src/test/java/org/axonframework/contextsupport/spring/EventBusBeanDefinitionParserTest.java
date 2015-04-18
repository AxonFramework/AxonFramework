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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-eventbus-namespace-support-context.xml"})
public class EventBusBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void eventBusElement() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("eventBus");
        assertNotNull("Bean definition not created", beanDefinition);
        assertEquals("Wrong bean class", SimpleEventBus.class.getName(), beanDefinition.getBeanClassName());
        assertEquals("wrong amount of constructor arguments"
                , 0, beanDefinition.getConstructorArgumentValues().getArgumentCount());

        EventBus eventBus = beanFactory.getBean("eventBus", EventBus.class);
        assertNotNull(eventBus);
    }

    @Test
    public void eventBusElementWithTerminal() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("eventBusTerminal");
        assertNotNull("Bean definition not created", beanDefinition);
        assertEquals("Wrong bean class", SimpleEventBus.class.getName(), beanDefinition.getBeanClassName());
        assertEquals("wrong amount of constructor arguments"
                , 1, beanDefinition.getConstructorArgumentValues().getArgumentCount());

        BeanReference terminalRef = (BeanReference) beanDefinition.getConstructorArgumentValues()
                                                                  .getArgumentValue(0, BeanReference.class)
                                                                  .getValue();
        assertEquals("constructor value is wrong", "terminal", terminalRef.getBeanName());
        assertNotNull(beanFactory.getBean("eventBusTerminal", EventBus.class));
    }
}
