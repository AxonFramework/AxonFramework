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

package org.axonframework.quickstart;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.ClassNamePatternClusterSelector;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusterSelector;
import org.axonframework.eventhandling.ClusteringEventBus;
import org.axonframework.eventhandling.CompositeClusterSelector;
import org.axonframework.eventhandling.DefaultClusterSelector;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.async.AsynchronousCluster;
import org.axonframework.eventhandling.async.FullConcurrencyPolicy;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;

/**
 * This example creates two clusters and assigns different listeners to each cluster. To prove things work, one cluster
 * is asynchronous and both listeners print the thread name when an event is received.
 *
 * @author Allard Buijze
 */
public class RunClusteringEventBus {

    public static void main(String[] args) {
        // since we're going to do asynchronous handling, we need an Executor. We provide a custom ThreadFactory
        // to choose the name of the created thread.
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // we initialize the asynchronous cluster. We don't need transactions (NoTransactionManager) and allow all
        // events to be handled concurrently (FullConcurrencyPolicy).
        Cluster asyncCluster = new AsynchronousCluster("async", executor, new FullConcurrencyPolicy());
        // and we initialize a simple cluster
        Cluster standardCluster = new SimpleCluster("simple");

        // to make sure Listeners are assigned to their respective cluster, we create a number of selectors that we
        // combine into a single ClusterSelector using a CompositeClusterSelector
        ClusterSelector clusterSelector = new CompositeClusterSelector(Arrays.<ClusterSelector>asList(
                // this one will accept
                new ClassNamePatternClusterSelector(Pattern.compile(".*Another.*"), asyncCluster),
                new DefaultClusterSelector(standardCluster)
        ));

        // create the event bus and subscribe two listeners
        // notice how the registration process itself is unaware of clustering
        EventBus eventBus = new ClusteringEventBus(clusterSelector);
        AnnotationEventListenerAdapter.subscribe(new ThreadPrintingEventListener(), eventBus);
        AnnotationEventListenerAdapter.subscribe(new AnotherThreadPrintingEventListener(), eventBus);

        // publish an event
        eventBus.publish(asEventMessage(new ToDoItemCompletedEvent("todo1")));

        // we need to shutdown the executor we have created to prevent the JVM from hanging on shutdown
        // this also wait until all scheduled tasks for that thread have been executed
        executor.shutdown();
    }

    private static class ThreadPrintingEventListener {

        @EventHandler
        public void onEvent(EventMessage event) {
            System.out.println("Received " + event.getPayload().toString() + " on thread named "
                                       + Thread.currentThread().getName());
        }
    }

    private static class AnotherThreadPrintingEventListener extends ThreadPrintingEventListener {

    }
}
