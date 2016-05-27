/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.common.DirectExecutor;
import org.axonframework.eventsourcing.AbstractSnapshotter;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.Snapshotter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class SnapshotterBeanDefinitionParserTest {

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Autowired
    @Qualifier("snapshotter")
    private Snapshotter snapshotter;

    @Autowired
    @Qualifier("inThreadsnapshotter")
    private Snapshotter inThreadSnapshotter;

    @Test
    public void testSnapshotterInitialization() throws NoSuchFieldException, IllegalAccessException {
        BeanDefinition definition = beanFactory.getBeanDefinition("snapshotter");
        assertNotNull("BeanDefinition not created", definition);
        assertEquals("Wrong bean class", AggregateSnapshotter.class.getName(), definition.getBeanClassName());

        assertNotNull("snapshotter not configured properly", snapshotter);
        assertNotNull("inThreadsnapshotter not configured properly", inThreadSnapshotter);
        assertEquals(DirectExecutor.INSTANCE, getExecutorFrom(inThreadSnapshotter));
        assertNotNull(getTransactionManagerFrom(snapshotter));
    }

    private Object getExecutorFrom(Snapshotter snapshotter) throws NoSuchFieldException, IllegalAccessException {
        Field snapshotterField = AbstractSnapshotter.class.getDeclaredField("executor");
        snapshotterField.setAccessible(true);
        return snapshotterField.get(snapshotter);
    }

    private Object getTransactionManagerFrom(Snapshotter snapshotter)
            throws NoSuchFieldException, IllegalAccessException {
        Field snapshotterField = AggregateSnapshotter.class.getDeclaredField("transactionManager");
        snapshotterField.setAccessible(true);
        return snapshotterField.get(snapshotter);
    }
}
