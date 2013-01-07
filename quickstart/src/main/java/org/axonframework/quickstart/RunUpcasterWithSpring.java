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

import org.apache.commons.io.FileUtils;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.IOException;

/**
 * @author Allard Buijze
 */
public class RunUpcasterWithSpring {

    public static void main(String[] args) throws IOException {
        // we want to delete the directory that will store our events
        FileUtils.deleteDirectory(new File(System.getProperty("java.io.tmpdir"), "Events"));

        // we start the application context
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("upcaster-config.xml");

        // we fetch the EventStore from the application context
        EventStore eventStore = applicationContext.getBean(EventStore.class);

        // we append some events. Notice we append a "ToDoItemCreatedEvent".
        eventStore.appendEvents("UpcasterSample", new SimpleDomainEventStream(
                new GenericDomainEventMessage("todo1", 0, new ToDoItemCreatedEvent("todo1", "I need to do this today")),
                new GenericDomainEventMessage("todo1", 1, new ToDoItemCompletedEvent("todo1"))
        ));
        eventStore.appendEvents("UpcasterSample", new SimpleDomainEventStream(
                new GenericDomainEventMessage("todo2", 0, new ToDoItemCreatedEvent("todo2", "I also need to do this"))
        ));


        // now, we read the events from the "todo1" stream
        DomainEventStream upcastEvents = eventStore.readEvents("UpcasterSample", "todo1");
        while (upcastEvents.hasNext()) {
            // and print them, so that we can see what we ended up with
            System.out.println(upcastEvents.next().getPayload().toString());
        }
        // to see the Upcaster doing the upcasting, see RunUpcaster, inner class ToDoItemUpcaster

        // we close the application context. It's just good habit
        applicationContext.close();
    }
}
