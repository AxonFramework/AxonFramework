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

import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.disruptor.DisruptorConfiguration;
import org.axonframework.commandhandling.model.Repository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DisruptorCommandBusBeanDefinitionParserTest.Context.class)
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
        Object repo3 = beanFactory.getBean("theThirdAggregateRepository");
        assertTrue(repo1 instanceof Repository);
        assertTrue(repo2 instanceof Repository);
        assertTrue(repo3 instanceof Repository);
        assertNotSame(repo1, repo2);
        assertNotSame(repo1, repo3);
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
        assertTrue(config.getPropertyValues().getPropertyValue("serializer").getValue() instanceof BeanReference);
        assertEquals("2048", config.getPropertyValues().getPropertyValue("bufferSize").getValue());
        assertEquals("3", config.getPropertyValues().getPropertyValue("serializerThreadCount").getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInternalRepositoryContainsDecorator() {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition)
                beanFactory.getBeanDefinition("myAggregateRepository");
        PropertyValue triggerValue = beanDefinition.getPropertyValues().getPropertyValue("snapshotterTrigger");
        assertNotNull(triggerValue);
        final GenericBeanDefinition snapshotterDefinition = (GenericBeanDefinition) triggerValue.getValue();
        assertEquals("org.axonframework.eventsourcing.EventCountSnapshotterTrigger",
                     snapshotterDefinition.getBeanClassName());
        assertEquals("10", snapshotterDefinition.getPropertyValues().getPropertyValue("trigger").getValue());
        assertEquals(new RuntimeBeanReference("snapshotter"),
                     snapshotterDefinition.getPropertyValues().getPropertyValue("snapshotter").getValue());

        PropertyValue decoratorsValue = beanDefinition.getPropertyValues().getPropertyValue("eventStreamDecorators");
        assertNotNull(decoratorsValue);
        final List<BeanDefinitionHolder> decorators = (List<BeanDefinitionHolder>) decoratorsValue.getValue();
        assertEquals(1, decorators.size());
        assertEquals("org.axonframework.spring.testutils.MockitoMockFactoryBean",
                     decorators.get(0).getBeanDefinition().getBeanClassName());

    }

    @Test
    public void testExternalRepositoryContainsDecorator() {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition)
                beanFactory.getBeanDefinition("theThirdAggregateRepository");
        PropertyValue triggerValue = beanDefinition.getPropertyValues().getPropertyValue("snapshotterTrigger");
        assertNotNull(triggerValue);
        final GenericBeanDefinition snapshotterDefinition = (GenericBeanDefinition) triggerValue.getValue();
        assertEquals("org.axonframework.eventsourcing.EventCountSnapshotterTrigger",
                     snapshotterDefinition.getBeanClassName());
        assertEquals("10", snapshotterDefinition.getPropertyValues().getPropertyValue("trigger").getValue());
        assertEquals(new RuntimeBeanReference("snapshotter"),
                     snapshotterDefinition.getPropertyValues().getPropertyValue("snapshotter").getValue());
        assertEquals(new RuntimeBeanReference("mockCache"),
                     snapshotterDefinition.getPropertyValues().getPropertyValue("aggregateCache").getValue());
    }

    /**
     * Spring configuration to test for <a href="http://issues.axonframework.org/youtrack/issue/AXON-159">AXON-159</a>
     */
    @Configuration
    @ImportResource({"classpath:contexts/disruptor-context.xml"})
    public static class Context {

        @Bean
        public String myBean(@Qualifier("myOtherAggregateRepository")Repository repository) {
            assertNotNull(repository);
            return "";
        }

        @Bean
        public String myOtherBean(@Qualifier("myAggregateRepository")Repository repository) {
            assertNotNull(repository);
            return "";
        }

    }
}
