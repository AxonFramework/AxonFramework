package org.axonframework.contextsupport.spring;

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusterSelector;
import org.axonframework.eventhandling.SimpleCluster;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class ClusterBeanDefinitionParserTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testBeansAreProperlyConfigured() {
        Cluster cluster1 = applicationContext.getBean("firstCluster", Cluster.class);
        Cluster cluster2 = applicationContext.getBean("defaultCluster", Cluster.class);
        ClusterSelector selector1 = applicationContext.getBean("firstCluster$selector", ClusterSelector.class);
        ClusterSelector selector2 = applicationContext.getBean("defaultCluster$defaultSelector", ClusterSelector.class);

        assertNotNull(cluster1);
        assertNotNull(cluster2);
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


}
