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

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventsourcing.HybridJpaRepository;
import org.axonframework.repository.GenericJpaRepository;
import org.axonframework.repository.LockManager;
import org.axonframework.repository.OptimisticLockManager;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class JpaRepositoryBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testRepositoryWithDefaults() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("simpleJpaRepository");
        assertEquals(2, beanDefinition.getConstructorArgumentValues().getArgumentCount());
        assertEquals(GenericJpaRepository.class.getName(), beanDefinition.getBeanClassName());

        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(0, EntityManagerProvider.class));
        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(1, Class.class));

        assertTrue(beanDefinition.getPropertyValues().getPropertyValue("eventBus")
                                 .getValue() instanceof BeanDefinition);
        final BeanDefinition eventBusDefinition = (BeanDefinition) beanDefinition.getPropertyValues()
                                                                                 .getPropertyValue("eventBus")
                                                                                 .getValue();
        assertEquals(AutowiredDependencyFactoryBean.class.getName(), eventBusDefinition.getBeanClassName());
    }

    @Test
    public void testRepositoryWithLockingStrategy() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("optimisticJpaRepository");
        assertEquals(3, beanDefinition.getConstructorArgumentValues().getArgumentCount());
        assertEquals(GenericJpaRepository.class.getName(), beanDefinition.getBeanClassName());

        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(0, EntityManagerProvider.class));
        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(1, Class.class));
        final ConstructorArgumentValues.ValueHolder argumentValue =
                beanDefinition.getConstructorArgumentValues().getArgumentValue(2, BeanDefinition.class);
        assertNotNull(argumentValue);
        assertTrue("Expected a BeanDefinition", argumentValue.getValue() instanceof BeanDefinition);

        final BeanDefinition lockManagerBeanDefinition = (BeanDefinition) argumentValue.getValue();
        assertEquals(OptimisticLockManager.class.getName(), lockManagerBeanDefinition.getBeanClassName());
    }

    @Test
    public void testRepositoryWithAllPropertiesDefined() {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("fullyDefinedJpaRepository");
        assertEquals(3, beanDefinition.getConstructorArgumentValues().getArgumentCount());

        assertEquals(HybridJpaRepository.class.getName(), beanDefinition.getBeanClassName());

        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(0, EntityManagerProvider.class));
        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(1, Class.class));
        assertNotNull(beanDefinition.getConstructorArgumentValues().getArgumentValue(2, LockManager.class));

        assertTrue(beanDefinition.getPropertyValues().getPropertyValue("eventBus")
                                 .getValue() instanceof BeanReference);
        assertTrue(beanDefinition.getPropertyValues().getPropertyValue("eventStore")
                                 .getValue() instanceof BeanReference);
    }
}
