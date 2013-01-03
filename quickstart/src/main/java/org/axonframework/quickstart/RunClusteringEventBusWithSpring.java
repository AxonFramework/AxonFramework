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

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;

/**
 * @author Allard Buijze
 */
public class RunClusteringEventBusWithSpring {

    public static void main(String[] args) throws InterruptedException {
        // We start up a Spring context. In web applications, this is normally done through a context listener
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("cluster-config.xml");

        // we get the event bus from the application context
        EventBus eventBus = applicationContext.getBean(EventBus.class);
        eventBus.publish(asEventMessage(new ToDoItemCompletedEvent("todo1")));

        // we close the application context to make sure everything shuts down properly
        applicationContext.stop();
        applicationContext.close();
    }

    public static class ThreadPrintingEventListener {

        @EventHandler
        public void onEvent(EventMessage event) {
            System.out.println("Received " + event.getPayload().toString() + " on thread named "
                                       + Thread.currentThread().getName());
        }
    }

    public static class AnotherThreadPrintingEventListener extends ThreadPrintingEventListener {

    }
}
