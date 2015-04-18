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

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.OrderResolver;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.SpringAnnotationOrderResolver;
import org.axonframework.spring.eventhandling.ClusterSelector;
import org.junit.*;
import org.junit.runner.*;
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
public class ClusterBeanDefinitionParserTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testBeansAreProperlyConfigured() {
        Cluster cluster1 = applicationContext.getBean("firstCluster", Cluster.class);
        Cluster cluster2 = applicationContext.getBean("defaultCluster", Cluster.class);
        Cluster cluster3 = applicationContext.getBean("replayingCluster", Cluster.class);
        Cluster cluster4 = applicationContext.getBean("defaultOrderedCluster", Cluster.class);
        Cluster cluster5 = applicationContext.getBean("customOrderedCluster", Cluster.class);
        ClusterSelector selector1 = applicationContext.getBean("firstCluster$selector", ClusterSelector.class);
        ClusterSelector selector2 = applicationContext.getBean("defaultCluster$defaultSelector", ClusterSelector.class);

        assertNotNull(cluster1);
        assertNotNull(cluster2);
        assertNotNull(cluster3);
        assertNotNull(cluster4);
        assertNotNull(cluster5);
        assertNotNull(selector1);
        assertNotNull(selector2);

        assertEquals(SimpleCluster.class, cluster1.getClass());
        assertEquals("value", cluster1.getMetaData().getProperty("meta"));
        assertFalse(cluster2.getMetaData().isPropertySet("meta"));

        assertTrue(selector1 instanceof Ordered);
        assertEquals(2, ((Ordered) selector1).getOrder());

        assertTrue(selector2 instanceof Ordered);
        assertEquals(Ordered.LOWEST_PRECEDENCE, ((Ordered) selector2).getOrder());
    }

    @Test
    public void testDefaultOrderedClusterConfiguration() {
        BeanDefinition bd = beanFactory.getBeanDefinition("defaultOrderedCluster");
        BeanDefinition clusterBeanDefinition = (BeanDefinition) bd.getConstructorArgumentValues().getArgumentValue(0, Object.class).getValue();
        assertEquals(2, clusterBeanDefinition.getConstructorArgumentValues().getArgumentCount());
        Object orderResolver = clusterBeanDefinition.getConstructorArgumentValues().getArgumentValue(1, Object.class)
                                             .getValue();
        assertTrue(orderResolver instanceof BeanDefinition);
        assertEquals(SpringAnnotationOrderResolver.class.getName(), ((BeanDefinition) orderResolver).getBeanClassName());
    }

    @Test
    public void testCustomOrderedClusterBeanDefinition() {
        BeanDefinition bd = beanFactory.getBeanDefinition("customOrderedCluster");
        BeanDefinition clusterBeanDefinition = (BeanDefinition) bd.getConstructorArgumentValues().getArgumentValue(0, Object.class).getValue();
        assertEquals(2, clusterBeanDefinition.getConstructorArgumentValues().getArgumentCount());
        Object orderResolver = clusterBeanDefinition.getConstructorArgumentValues().getArgumentValue(1, Object.class)
                                             .getValue();
        assertTrue(orderResolver instanceof RuntimeBeanReference);
        assertEquals("orderResolver", ((RuntimeBeanReference) orderResolver).getBeanName());
    }

    @Test
    public void testCustomOrderedClusterBean() {
        Cluster cluster = applicationContext.getBean("customOrderedCluster", Cluster.class);
        final EventListener listener = mock(EventListener.class);
        final EventListener listener2 = mock(EventListener.class);
        cluster.subscribe(listener);
        cluster.subscribe(listener2);
        OrderResolver mockOrderResolver = applicationContext.getBean("orderResolver", OrderResolver.class);
        verify(mockOrderResolver, atLeastOnce()).orderOf(listener);
        verify(mockOrderResolver, atLeastOnce()).orderOf(listener2);
    }
}
