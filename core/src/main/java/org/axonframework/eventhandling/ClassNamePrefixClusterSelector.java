/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * ClusterSelector implementation that chooses a Cluster based on a mapping of the Listener's Class Name. It maps a
 * prefix to a Cluster. When two prefixes match the same class, the Cluster mapped to the longest prefix is chosen.
 * <p/>
 * For example, consider the following mappings: <pre>
 * org.axonframework -> cluster1
 * com.              -> cluster2
 * com.somecompany   -> cluster3
 * </pre>
 * A class named <code>com.somecompany.SomeListener</code> will map to <code>cluster3</code> as it is mapped to a more
 * specific prefix. This implementation uses {@link String#startsWith(String)} to evaluate a match.
 * <p/>
 * Note that the name of the class used is the name of the class implementing the <code>EventListener</code> interface.
 * If a listener implements the <code>EventListenerProxy</code> interface, the value of the {@link
 * org.axonframework.eventhandling.EventListenerProxy#getTargetType()} is used. Annotated Event Listeners will always
 * have the actual annotated class name used.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ClassNamePrefixClusterSelector implements ClusterSelector {

    private final Map<String, Cluster> mappings;
    private final Cluster defaultCluster;

    /**
     * Initializes the ClassNamePrefixClusterSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the Cluster Selector returns <code>null</code>.
     *
     * @param mappings the mappings defining a cluster for each Class Name prefix
     */
    public ClassNamePrefixClusterSelector(Map<String, Cluster> mappings) {
        this(mappings, null);
    }

    /**
     * Initializes the ClassNamePrefixClusterSelector using the given <code>mappings</code>. If a name does not have a
     * prefix defined, the Cluster Selector returns the given <code>defaultCluster</code>.
     *
     * @param mappings       the mappings defining a cluster for each Class Name prefix
     * @param defaultCluster The cluster to use when no mapping is present
     */
    public ClassNamePrefixClusterSelector(Map<String, Cluster> mappings, Cluster defaultCluster) {
        this.defaultCluster = defaultCluster;
        this.mappings = new TreeMap<String, Cluster>(new ReverseStringComparator());
        this.mappings.putAll(mappings);
    }

    @Override
    public Cluster selectCluster(EventListener eventListener) {
        Class<?> listenerType;
        if (eventListener instanceof EventListenerProxy) {
            listenerType = ((EventListenerProxy) eventListener).getTargetType();
        } else {
            listenerType = eventListener.getClass();
        }
        String listenerName = listenerType.getName();
        for (Map.Entry<String, Cluster> entry : mappings.entrySet()) {
            if (listenerName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return defaultCluster;
    }

    private static class ReverseStringComparator implements Comparator<String>, Serializable {

        private static final long serialVersionUID = -1653838988719816515L;

        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }
}
