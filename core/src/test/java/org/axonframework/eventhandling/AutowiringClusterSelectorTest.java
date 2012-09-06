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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(classes = {})
@RunWith(SpringJUnit4ClassRunner.class)
public class AutowiringClusterSelectorTest {

    private static final SimpleCluster CLUSTER_1 = new SimpleCluster();
    private static final SimpleCluster CLUSTER_2 = new SimpleCluster();
    private static final SimpleCluster CLUSTER_3 = new SimpleCluster();

    @Autowired
    private AutowiringClusterSelector testSubject;

    @Autowired
    @Qualifier("firstSelector")
    private ClusterSelector firstSelector;

    @Autowired
    @Qualifier("secondSelector")
    private ClusterSelector secondSelector;

    @Autowired
    @Qualifier("thirdSelector")
    private ClusterSelector thirdSelector;

    @Before
    public void setUp() {
        reset(firstSelector, secondSelector, thirdSelector);
    }

    @Test
    public void testAutowiringClusterSelector_LastCandidateSelected() {
        //This will also test for a recursive dependency of the autowired cluster selector on itself
        assertNotNull(testSubject);
        Cluster cluster = testSubject.selectCluster(new EventListener() {
            @Override
            public void handle(EventMessage event) {
            }
        });
        verify(firstSelector).selectCluster(isA(EventListener.class));
        verify(secondSelector).selectCluster(isA(EventListener.class));
        verify(thirdSelector).selectCluster(isA(EventListener.class));
        assertSame(CLUSTER_3, cluster);
    }

    @Test
    public void testAutowiringClusterSelector_FirstCandidateSelected() {
        assertNotNull(testSubject);

        Cluster cluster = testSubject.selectCluster(new MyTestListener());
        assertSame(CLUSTER_1, cluster);
        verify(firstSelector).selectCluster(isA(EventListener.class));
        verify(secondSelector, never()).selectCluster(isA(EventListener.class));
        verify(thirdSelector, never()).selectCluster(isA(EventListener.class));
    }

    @Configuration
    public static class TestContext {

        @Bean
        public AutowiringClusterSelector testSubject() {
            return new AutowiringClusterSelector();
        }

        @Bean
        public ClusterSelector firstSelector() {
            return spy(new OrderedSelector(Integer.MIN_VALUE,
                                           new ClassNamePatternClusterSelector(Pattern.compile(".*TestListener"),
                                                                               CLUSTER_1)));
        }

        @Bean
        public ClusterSelector secondSelector() {
            return spy(new ClassNamePrefixClusterSelector("java", CLUSTER_2));
        }

        @Bean
        public ClusterSelector thirdSelector() {
            return spy(new OrderedSelector(Integer.MAX_VALUE, new ClassNamePrefixClusterSelector("org", CLUSTER_3)));
        }
    }

    private static class OrderedSelector implements Ordered, ClusterSelector {

        private final int order;
        private final ClusterSelector delegate;

        private OrderedSelector(int order, ClusterSelector delegate) {
            this.order = order;
            this.delegate = delegate;
        }

        @Override
        public Cluster selectCluster(EventListener eventListener) {
            return delegate.selectCluster(eventListener);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    private static class MyTestListener implements EventListener {

        @Override
        public void handle(EventMessage event) {
        }
    }
}
