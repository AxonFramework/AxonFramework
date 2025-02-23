/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;


import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

class StatefulSingleCommandHandlingComponentTest {

    public static final String DEFAULT_CONTEXT = "default";
    private final Logger logger = LoggerFactory.getLogger(StatefulSingleCommandHandlingComponentTest.class);

    @Test
    void name() throws ExecutionException, InterruptedException {
        SimpleEventStore eventStore = new SimpleEventStore(
                new AsyncInMemoryEventStorageEngine(),
                DEFAULT_CONTEXT,
                new AnnotationBasedTagResolver()
        );

        AsyncEventSourcingRepository<String, Student> studentRepository = new AsyncEventSourcingRepository<>(
                eventStore,
                myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Student", myModelId)),
                (model, em) -> {
                    if (em.getPayload() instanceof StudentNameChangedEvent) {
                        logger.info("Handling ES event: {}", em.getPayload());
                        model.setName(((StudentNameChangedEvent) em.getPayload()).name());
                    }
                    return model;
                },
                Student::new,
                DEFAULT_CONTEXT
        );
        Function<CommandMessage<?>, String> commandIdResolver = command -> {
            if (command.getPayload() instanceof ChangeStudentNameCommand(String id, String name)) {
                return id;
            }
            throw new IllegalArgumentException("Unsupported command");
        };

        var component = StatefulCommandHandlingComponent
                .forName("MyStatefulCommandHandlingComponent")
                .loadModelsEagerly()
                .registerModel(
                        Student.class,
                        commandIdResolver,
                        (id, context) -> studentRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
                )
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            Student student = model.modelOf(Student.class);
                            logger.info("Handling command: {}. Models resolved before: {}", command, student);
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            eventStore.transaction(context, DEFAULT_CONTEXT)
                                      .appendEvent(new GenericEventMessage<>(
                                              new MessageType(StudentNameChangedEvent.class),
                                              new StudentNameChangedEvent(student.id, payload.name())));

                            logger.info("Model after: {}", model);
                            return MessageStream.empty().cast();
                        });

        updateName("name-1", component);
        updateName("name-2", component);
        updateName("name-3", component);
        updateName("name-4", component);
        updateName("name-5", component);
    }

    private static void updateName(String name2, StatefulCommandHandlingComponent component)
            throws InterruptedException, ExecutionException {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            GenericCommandMessage<ChangeStudentNameCommand> command = new GenericCommandMessage<>(
                    new MessageType(ChangeStudentNameCommand.class),
                    new ChangeStudentNameCommand("my-studentId", name2));
            return component.handle(command, context).first().asCompletableFuture();
        }).get();
    }


    record ChangeStudentNameCommand(
            String id,
            String name
    ) {

    }

    static class Student {

        private String id;
        private String name;
        private List<String> coursedEnrolled = new ArrayList<>();

        public Student(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getCoursedEnrolled() {
            return coursedEnrolled;
        }

        public boolean enroll(String courseId) {
            if (coursedEnrolled.contains(courseId)) {
                return false;
            }
            if (coursedEnrolled.size() > 2) {
                return false;
            }
            coursedEnrolled.add(courseId);
            return true;
        }

        @Override
        public String toString() {
            return id + " " + name;
        }
    }


    record StudentNameChangedEvent(
            @EventTag(key = "Student")
            String id,
            String name
    ) {

    }
}