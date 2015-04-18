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

package org.axonframework.spring.eventhandling;

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventListener;
import org.junit.*;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CompositeClusterSelectorTest {

    private CompositeClusterSelector testSubject;
    private EventListener mockListener;
    private ClusterSelector selector1;
    private ClusterSelector selector2;
    private ClusterSelector selector3;
    private Cluster cluster;

    @Before
    public void setUp() throws Exception {
        mockListener = mock(EventListener.class);
        selector1 = mock(ClusterSelector.class);
        selector2 = mock(ClusterSelector.class);
        selector3 = mock(ClusterSelector.class);
        cluster = mock(Cluster.class);
    }

    @Test
    public void testSelectorDelegatesInOrder() throws Exception {
        Cluster cluster = mock(Cluster.class);

        when(selector2.selectCluster(isA(EventListener.class))).thenReturn(cluster);
        testSubject = new CompositeClusterSelector(Arrays.asList(selector1, selector2, selector3));

        Cluster actual = testSubject.selectCluster(mockListener);
        assertSame(cluster, actual);
        verify(selector1).selectCluster(mockListener);
        verify(selector2).selectCluster(mockListener);
        verify(selector3, never()).selectCluster(any(EventListener.class));
    }

    @Test
    public void testSelectorDelegatesInOrder_NoClusterFound() throws Exception {
        testSubject = new CompositeClusterSelector(Arrays.asList(selector1, selector2, selector3));

        Cluster actual = testSubject.selectCluster(mockListener);
        assertNull(actual);
        verify(selector1).selectCluster(mockListener);
        verify(selector2).selectCluster(mockListener);
        verify(selector3).selectCluster(mockListener);
    }
}
