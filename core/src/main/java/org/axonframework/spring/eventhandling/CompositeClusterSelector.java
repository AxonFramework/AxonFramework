/*
 * Copyright (c) 2010-2014. Axon Framework
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ClusterSelector implementation that delegates the selection to a list of other ClusterSelectors. The first of the
 * delegates is asked for a Cluster. If it provides none, the second is invoked, and so forth, until a delegate returns
 * a Cluster instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CompositeClusterSelector implements ClusterSelector {

    private final List<ClusterSelector> delegates;

    /**
     * Initializes the CompositeClusterSelector with the given List of <code>delegates</code>. The delegates are
     * evaluated in the order provided by the List's iterator.
     *
     * @param delegates the delegates to evaluate
     */
    public CompositeClusterSelector(List<ClusterSelector> delegates) {
        this.delegates = new ArrayList<>(delegates);
    }

    @Override
    public Cluster selectCluster(EventListener eventListener) {
        Cluster selected = null;
        Iterator<ClusterSelector> iterator = delegates.iterator();
        while (selected == null && iterator.hasNext()) {
            selected = iterator.next().selectCluster(eventListener);
        }
        return selected;
    }
}
