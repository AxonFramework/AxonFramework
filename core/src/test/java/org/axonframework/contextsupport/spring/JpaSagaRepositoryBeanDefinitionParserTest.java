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

import org.axonframework.common.NoCache;
import org.axonframework.saga.repository.CachingSagaRepository;
import org.axonframework.saga.repository.jpa.JpaSagaRepository;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class JpaSagaRepositoryBeanDefinitionParserTest {

    @Autowired
    private ApplicationContext applicationContext;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Test
    public void testWiringRepository() {
        BeanDefinition beanDef = beanFactory.getBeanDefinition("allDefinedSagaRepository");
        assertEquals(JpaSagaRepository.class.getName(), beanDef.getBeanClassName());
    }

    @Test
    public void testCachingSagaRepositoryWiring() {
        BeanDefinition beanDef = beanFactory.getBeanDefinition("cachingSagaRepository");
        assertEquals(CachingSagaRepository.class.getName(), beanDef.getBeanClassName());
        assertEquals(3, beanDef.getConstructorArgumentValues().getArgumentCount());
        assertNotSame(NoCache.INSTANCE, getConstructorArgumentValue(beanDef, 1));
        assertNotSame(NoCache.INSTANCE, getConstructorArgumentValue(beanDef, 2));

        assertNotNull(applicationContext.getBean("cachingSagaRepository"));
    }

    @Test
    public void testCachingSagaRepositoryWiring_NoCachesDefined() {
        BeanDefinition beanDef = beanFactory.getBeanDefinition("noCacheSagaRepository");
        assertEquals(CachingSagaRepository.class.getName(), beanDef.getBeanClassName());
        assertEquals(3, beanDef.getConstructorArgumentValues().getArgumentCount());
        assertSame(NoCache.INSTANCE, getConstructorArgumentValue(beanDef, 1));
        assertSame(NoCache.INSTANCE, getConstructorArgumentValue(beanDef, 2));

        assertNotNull(applicationContext.getBean("noCacheSagaRepository"));
    }

    private Object getConstructorArgumentValue(BeanDefinition beanDef, int index) {
        return beanDef.getConstructorArgumentValues().getIndexedArgumentValue(index, Object.class).getValue();
    }
}
