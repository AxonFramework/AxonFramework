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
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.eventstore.fs.SimpleEventFileResolver;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedType;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.AbstractSingleEntryUpcaster;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcastingContext;
import org.dom4j.Document;
import org.dom4j.Element;
import org.joda.time.DateTime;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author Allard Buijze
 */
public class RunUpcaster {

    public static void main(String[] args) throws IOException {
        // we want to delete the directory that will store our events
        final File eventsDir = new File(System.getProperty("java.io.tmpdir"), "Events");
        FileUtils.deleteDirectory(eventsDir);

        // we create a serializer, so we can ensure the event store and the upcasters use the same configuration
        Serializer serializer = new XStreamSerializer();
        // initialize a FileSystem Event Store
        FileSystemEventStore eventStore = new FileSystemEventStore(serializer, new SimpleEventFileResolver(eventsDir));
        // initialize the upcaster chain with our upcaster
        eventStore.setUpcasterChain(new LazyUpcasterChain(serializer,
                                                          Collections.<Upcaster>singletonList(new ToDoItemUpcaster())));

        // we append some events. Notice we append a "ToDoItemCreatedEvent".
        eventStore.appendEvents("UpcasterSample", new SimpleDomainEventStream(
                new GenericDomainEventMessage("todo1", 0, new ToDoItemCreatedEvent("todo1", "I need to do this today")),
                new GenericDomainEventMessage("todo1", 1, new ToDoItemCompletedEvent("todo1"))
        ));
        eventStore.appendEvents("UpcasterSample", new SimpleDomainEventStream(
                new GenericDomainEventMessage("todo2", 0, new ToDoItemCreatedEvent("todo2", "I also need to do this"))
        ));

        // now, we read the events from the "todo1" stream
        DomainEventStream eventStream = eventStore.readEvents("UpcasterSample", "todo1");
        while (eventStream.hasNext()) {
            // and print them, so that we can see what we ended up with
            System.out.println(eventStream.next().getPayload().toString());
        }
    }

    /**
     * This is our upcaster. It converts the XML representation of a ToItemCreatedEvent to a
     * NewToDoItemWithDeadlineCreatedEvent. The latter contains an explicit deadline of the task at hand.
     */
    public static class ToDoItemUpcaster extends AbstractSingleEntryUpcaster<Document> {

        @Override
        public boolean canUpcast(SerializedType serializedType) {
            // we can upcast the object if it's type name is the fully qualified class name of the ToDoItemCreatedEvent.
            // normally, you would also want to check the revision
            return ToDoItemCreatedEvent.class.getName().equals(serializedType.getName());
        }

        @Override
        public Class<Document> expectedRepresentationType() {
            // we want to use Dom4J document. Axon will automatically convert the serialized form.
            return Document.class;
        }

        @Override
        public Document doUpcast(SerializedObject<Document> intermediateRepresentation,
                                 UpcastingContext context) {
            // here, we convert the XML format of the old event to that of the new event
            Document data = intermediateRepresentation.getData();
            Element rootElement = data.getRootElement();
            // change the name of the root element to reflect the changed class name
            rootElement.setName(NewToDoItemWithDeadlineCreatedEvent.class.getName());
            // and add an element for the new "deadline" field
            rootElement.addElement("deadline")
                    // we set the value of the field to the default value: one day after the event was created
                    .setText(context.getTimestamp().plusDays(1).toString());
            // we return the modified Document
            return data;
        }

        @Override
        public SerializedType doUpcast(SerializedType serializedType) {
            // we describe the refactoring that we have done. Since we want to simulate a new revision and need to
            // change the class name, we pass both details in the returned SerializedType.
            return new SimpleSerializedType(NewToDoItemWithDeadlineCreatedEvent.class.getName(), "1.1");
        }
    }

    /**
     * This class represents the refactored ToDoItemCreatedEvent
     */
    public static class NewToDoItemWithDeadlineCreatedEvent {

        private final String todoId;
        private final String description;
        private final DateTime deadline;

        public NewToDoItemWithDeadlineCreatedEvent(String todoId, String description, DateTime deadline) {
            this.todoId = todoId;
            this.description = description;
            this.deadline = deadline;
        }

        public String getTodoId() {
            return todoId;
        }

        public String getDescription() {
            return description;
        }

        public DateTime getDeadline() {
            return deadline;
        }

        @Override
        public String toString() {
            return "NewToDoItemWithDeadlineCreatedEvent(" + todoId + ", '" + description + "' before "
                    + deadline.toString("dd-MM-YYYY") + " at " + deadline.toString("HH:mm") + ")";
        }
    }
}
