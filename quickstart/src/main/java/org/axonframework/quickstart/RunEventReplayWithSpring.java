/*
 * Copyright (c) 2010-2013. Axon Framework
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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.replay.ReplayAware;
import org.axonframework.eventhandling.replay.ReplayingCluster;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.axonframework.quickstart.api.ToDoItemDeadlineExpiredEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;

/**
 * @author Allard Buijze
 */
public class RunEventReplayWithSpring {

    public static void main(String[] args) throws InterruptedException, TimeoutException, ExecutionException {
        // We start up a Spring context. In web applications, this is normally done through a context listener
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("replay-config.xml");

        // we get our resources from the application context
        StubEventStore eventStore = applicationContext.getBean("eventStore", StubEventStore.class);
        EventBus eventBus = applicationContext.getBean(EventBus.class);
        ExecutorService executor = applicationContext.getBean(ExecutorService.class);
        // and finally, the ReplayingCluster. As you can see the bean is an instance of ReplayingCluster, because we
        // added replay configuration to it.
        ReplayingCluster replayingCluster = applicationContext.getBean("cluster", ReplayingCluster.class);

        // we append some events to simulate a full event store
        final DomainEventMessage[] domainEventMessages = {
                new GenericDomainEventMessage<ToDoItemCreatedEvent>(
                        "todo1", 0, new ToDoItemCreatedEvent("todo1", "Need to do something")),
                new GenericDomainEventMessage<ToDoItemCreatedEvent>(
                        "todo2", 0, new ToDoItemCreatedEvent("todo2", "Another thing to do")),
                new GenericDomainEventMessage<ToDoItemCompletedEvent>("todo2", 0, new ToDoItemCompletedEvent("todo2"))
        };
        eventStore.appendEvents("mock", new SimpleDomainEventStream(domainEventMessages));

        // we get the executor service from the application context and start the replay as an asynchronous process
        Future<Void> future = replayingCluster.startReplay(executor);

        // we want to wait for the cluster to have switched to replay mode, so we can send some messages to it.
        // if we were to publish events right away, there is a big chance the Cluster didn't switch to replay mode, yet.
        waitForReplayToHaveStarted(replayingCluster);

        // this is a new event, so it should be backlogged and handled at the end of the replay
        eventBus.publish(asEventMessage(new ToDoItemCreatedEvent("todo3", "Came in just now...")));

        // this message is also part of the replay, and should therefore not be handled twice.
        eventBus.publish(domainEventMessages[2]);

        // we wait (at most 10 seconds) for the replay to complete.
        future.get(10, TimeUnit.SECONDS);

        // and we publish another event to show that it's handled in the calling thread
        eventBus.publish(asEventMessage(new ToDoItemDeadlineExpiredEvent("todo1")));

        // we want to shutdown the application context to get a clean JVM shutdown
        applicationContext.close();
    }

    private static void waitForReplayToHaveStarted(ReplayingCluster replayingCluster) {
        while (!replayingCluster.isInReplayMode()) {
            Thread.yield();
        }
    }

    public static class ThreadPrintingEventListener {

        @EventHandler
        public void onEvent(EventMessage event) {
            System.out.println(
                    "Received " + event.getPayload().toString() + " in " + getClass().getSimpleName()
                            + " on thread named "
                            + Thread.currentThread().getName());
        }
    }

    public static class AnotherThreadPrintingEventListener extends ThreadPrintingEventListener
            implements ReplayAware {

        @Override
        public void beforeReplay() {
            System.out.println("Seems like we're starting a replay");
        }

        @Override
        public void afterReplay() {
            System.out.println("Seems like we've done replaying");
        }
    }

    private static class StubEventStore implements EventStoreManagement, EventStore {

        private final List<DomainEventMessage> eventMessages = new CopyOnWriteArrayList<DomainEventMessage>();

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            while (events.hasNext()) {
                eventMessages.add(events.next());
            }
        }

        @Override
        public void visitEvents(EventVisitor visitor) {
            for (DomainEventMessage eventMessage : eventMessages) {
                visitor.doWithEvent(eventMessage);
            }
        }

        @Override
        public void visitEvents(Criteria criteria, EventVisitor visitor) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public CriteriaBuilder newCriteriaBuilder() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}
