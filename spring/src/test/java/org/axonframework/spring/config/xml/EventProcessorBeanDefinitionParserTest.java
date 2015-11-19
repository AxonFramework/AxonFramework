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

import org.axonframework.eventhandling.*;
import org.axonframework.spring.config.eventhandling.EventProcessorSelector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class EventProcessorBeanDefinitionParserTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testBeansAreProperlyConfigured() {
        EventProcessor eventProcessor1 = applicationContext.getBean("firstEventProcessor", EventProcessor.class);
        EventProcessor eventProcessor2 = applicationContext.getBean("defaultEventProcessor", EventProcessor.class);
        EventProcessor eventProcessor3 = applicationContext.getBean("replayingEventProcessor", EventProcessor.class);
        EventProcessor eventProcessor4 = applicationContext.getBean("defaultOrderedEventProcessor", EventProcessor.class);
        EventProcessor eventProcessor5 = applicationContext.getBean("customOrderedEventProcessor", EventProcessor.class);
        EventProcessorSelector selector1 = applicationContext.getBean("firstEventProcessor$selector", EventProcessorSelector.class);
        EventProcessorSelector selector2 = applicationContext.getBean("defaultEventProcessor$defaultSelector", EventProcessorSelector.class);

        assertNotNull(eventProcessor1);
        assertNotNull(eventProcessor2);
        assertNotNull(eventProcessor3);
        assertNotNull(eventProcessor4);
        assertNotNull(eventProcessor5);
        assertNotNull(selector1);
        assertNotNull(selector2);

        assertEquals(SimpleEventProcessor.class, eventProcessor1.getClass());
        assertEquals("value", eventProcessor1.getMetaData().getProperty("meta"));
        assertFalse(eventProcessor2.getMetaData().isPropertySet("meta"));

        assertTrue(selector1 instanceof Ordered);
        assertEquals(2, ((Ordered) selector1).getOrder());

        assertTrue(selector2 instanceof Ordered);
        assertEquals(Ordered.LOWEST_PRECEDENCE, ((Ordered) selector2).getOrder());
    }

    @Test
    public void testDefaultOrderedEventProcessorConfiguration() {
        BeanDefinition bd = beanFactory.getBeanDefinition("defaultOrderedEventProcessor");
        BeanDefinition processorBeanDefinition = (BeanDefinition) bd.getConstructorArgumentValues().getArgumentValue(0, Object.class).getValue();
        assertEquals(2, processorBeanDefinition.getConstructorArgumentValues().getArgumentCount());
        Object orderResolver = processorBeanDefinition.getConstructorArgumentValues().getArgumentValue(1, Object.class)
                                             .getValue();
        assertTrue(orderResolver instanceof BeanDefinition);
        assertEquals(SpringAnnotationOrderResolver.class.getName(), ((BeanDefinition) orderResolver).getBeanClassName());
    }

    @Test
    public void testCustomOrderedEventProcessorBeanDefinition() {
        BeanDefinition bd = beanFactory.getBeanDefinition("customOrderedEventProcessor");
        BeanDefinition processorBeanDefinition = (BeanDefinition) bd.getConstructorArgumentValues().getArgumentValue(0, Object.class).getValue();
        assertEquals(2, processorBeanDefinition.getConstructorArgumentValues().getArgumentCount());
        Object orderResolver = processorBeanDefinition.getConstructorArgumentValues().getArgumentValue(1, Object.class)
                                             .getValue();
        assertTrue(orderResolver instanceof RuntimeBeanReference);
        assertEquals("orderResolver", ((RuntimeBeanReference) orderResolver).getBeanName());
    }

    @Test
    public void testCustomOrderedEventProcessorBean() {
        EventProcessor eventProcessor = applicationContext.getBean("customOrderedEventProcessor", EventProcessor.class);
        final EventListener listener = mock(EventListener.class);
        final EventListener listener2 = mock(EventListener.class);
        eventProcessor.subscribe(listener);
        eventProcessor.subscribe(listener2);
        OrderResolver mockOrderResolver = applicationContext.getBean("orderResolver", OrderResolver.class);
        verify(mockOrderResolver, atLeastOnce()).orderOf(listener);
        verify(mockOrderResolver, atLeastOnce()).orderOf(listener2);
    }
}
