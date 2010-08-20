/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerBeanPostProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class AnnotationConfigurationBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testParseInternalElementParserContext() {
        BeanDefinition eventListenerDefinition = beanFactory.getBeanDefinition("__axon-annotation-event-listener-bean-post-processor");
        assertNotNull("Event listener bean post processor not defined", eventListenerDefinition);
        PropertyValue executorValue = eventListenerDefinition.getPropertyValues().getPropertyValue("executor");
        assertNotNull("Executor not defined", executorValue);
        Object value = executorValue.getValue();
        assertTrue("Wrong property value", RuntimeBeanReference.class.isInstance(value));
        RuntimeBeanReference beanReference = (RuntimeBeanReference) value;
        assertEquals("taskExecutor", beanReference.getBeanName());
        assertNull("Event bus should not be defined explicitly", eventListenerDefinition.getPropertyValues().getPropertyValue("eventBus"));

        AnnotationEventListenerBeanPostProcessor processor = beanFactory.getBean("__axon-annotation-event-listener-bean-post-processor", AnnotationEventListenerBeanPostProcessor.class);
        assertNotNull(processor);

        BeanDefinition commandHandlerDefinition = beanFactory.getBeanDefinition("__axon-annotation-command-handler-bean-post-processor");
        assertNotNull("Event listener bean post processor not defined", commandHandlerDefinition);
        executorValue = commandHandlerDefinition.getPropertyValues().getPropertyValue("commandBus");
        assertNotNull("Executor not defined", executorValue);
        value = executorValue.getValue();
        assertTrue("Wrong property value", RuntimeBeanReference.class.isInstance(value));
        beanReference = (RuntimeBeanReference) value;
        assertEquals("commandBus-embedded-ref", beanReference.getBeanName());
        assertNull("Event bus should not be defined explicitly", commandHandlerDefinition.getPropertyValues().getPropertyValue("eventBus"));

        AnnotationCommandHandlerBeanPostProcessor handler = beanFactory.getBean("__axon-annotation-command-handler-bean-post-processor", AnnotationCommandHandlerBeanPostProcessor.class);
        assertNotNull(handler);
    }

}
