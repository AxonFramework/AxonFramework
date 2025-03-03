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


import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.ModelRegistry;
import org.axonframework.modelling.SimpleModelRegistry;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.InjectModel;
import org.axonframework.modelling.command.annotation.InjectModelParameterResolverFactory;
import org.axonframework.modelling.command.annotation.TargetModelIdentifier;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the injection of a compound model based on a compound identifier that loads events of two tags. Currently is
 * disabled, as two requirements are not met:
 * <ol>
 *     <li>EventStore does not support multiple tags in a single query</li>
 *     <li>EventCriteria does not support OR on tags</li>
 * </ol>
 * In time, I expect this test to work, and for now it serves as an example.
 * NOTE: Using manual, temporary code edits this test WORKED.
 */
class CompoundModelIdentifierCommandHandlingComponentTest {

    public static final String DEFAULT_CONTEXT = "default";

    private final SimpleEventStore eventStore = new SimpleEventStore(
            new AsyncInMemoryEventStorageEngine(),
            DEFAULT_CONTEXT,
            new AnnotationBasedTagResolver()
    );

    private final EventStateApplier<StudentMentorCompoundModel> studentMentorCompoundModelEventStateApplier = new AnnotationBasedEventStateApplier<>(
            StudentMentorCompoundModel.class);

    private final AsyncEventSourcingRepository<MentorModelIdentifier, StudentMentorCompoundModel> studentMentorRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> {
                Set<Tag> tags = new HashSet<>();
                tags.add(new Tag("Student", myModelId.mentorId()));
                tags.add(new Tag("Student", myModelId.menteeId()));
                return EventCriteria.forAnyEventType().withTags(tags);
            },
            studentMentorCompoundModelEventStateApplier,
            StudentMentorCompoundModel::new,
            DEFAULT_CONTEXT
    );


    ModelRegistry registry = SimpleModelRegistry
            .create("MyModelRegistry")
            .registerModel(
                    MentorModelIdentifier.class,
                    StudentMentorCompoundModel.class,
                    (id, context) -> studentMentorRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
            );

    @Test
    @Disabled
    void canHandleCommandThatTargetsMultipleModelsViaInjectionOfCompoundModel() {

        var configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.getComponent(ModelRegistry.class)).thenReturn(registry);
        Mockito.when(configuration.getComponent(EventSink.class)).thenReturn(eventStore);

        CompoundModelAnnotatedCommandHandler handler = new CompoundModelAnnotatedCommandHandler();
        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", registry)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        new MultiParameterResolverFactory(
                                ClasspathParameterResolverFactory.forClass(this.getClass()),
                                // To be able to get components
                                new ConfigurationParameterResolverFactory(configuration),
                                // To be able to get the model, the ModelRegistry needs to be available.
                                // When the new configuration API is there, we should have a way to resolve this
                                new InjectModelParameterResolverFactory(registry)
                        )));

        // Can assign mentor to mentee
        sendCommand(component, new CompoundAssignMentorCommand("my-studentId-1", "my-studentId-2"));

        // But not a second time
        var exception = assertThrows(CompletionException.class,
                                     () -> sendCommand(component,
                                                       new CompoundAssignMentorCommand("my-studentId-1",
                                                                                       "my-studentId-3")
                                     ));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Mentee already has a mentor"));
    }

    private <T> void sendCommand(
            StatefulCommandHandlingComponent component,
            T payload
    ) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            GenericCommandMessage<T> command = new GenericCommandMessage<>(
                    new MessageType(payload.getClass()),
                    payload);
            return component.handle(command, context).first().asCompletableFuture();
        }).join();
    }


    record MentorModelIdentifier(
            String mentorId,
            String menteeId
    ) {

    }


    record CompoundAssignMentorCommand(
            String menteeId,
            String mentorId
    ) {

        @TargetModelIdentifier
        public MentorModelIdentifier modelIdentifier() {
            return new MentorModelIdentifier(mentorId, menteeId);
        }
    }

    record MentorAssignedToMenteeEvent(
            @EventTag(key = "Student")
            String mentorId,
            @EventTag(key = "Student")
            String menteeId
    ) {

    }

    static class StudentMentorCompoundModel {

        private MentorModelIdentifier identifier;
        private boolean mentorHasMentee;
        private boolean menteeHasMentor;

        public StudentMentorCompoundModel(MentorModelIdentifier identifier) {
            this.identifier = identifier;
        }

        public boolean isMentorHasMentee() {
            return mentorHasMentee;
        }

        public boolean isMenteeHasMentor() {
            return menteeHasMentor;
        }

        @EventSourcingHandler
        public void handle(MentorAssignedToMenteeEvent event) {
            if (event.mentorId().equals(this.identifier.mentorId())) {
                mentorHasMentee = true;
            } else if (event.menteeId().equals(this.identifier.menteeId())) {
                menteeHasMentor = true;
            }
        }
    }


    static class CompoundModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(CompoundAssignMentorCommand command,
                           @InjectModel StudentMentorCompoundModel model,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
            if (model.isMentorHasMentee()) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (model.isMenteeHasMentor()) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            eventSink.publish(context, DEFAULT_CONTEXT, new GenericEventMessage<>(
                    new MessageType(MentorAssignedToMenteeEvent.class),
                    new MentorAssignedToMenteeEvent(command.mentorId, command.menteeId)
            ));
        }
    }
}