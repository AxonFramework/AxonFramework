/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Marc Gathier
 */
@SuppressWarnings({"unchecked"})
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class AggregateWithUuidAnnotationCommandHandlerTest {

    private AggregateAnnotationCommandHandler<AggregateWithUUID> testSubject;
    private SimpleCommandBus commandBus;
    private Repository<AggregateWithUUID> mockRepository;
    private AggregateModel<AggregateWithUUID> aggregateModel;

    @BeforeEach
    void setUp() throws Exception {
        commandBus = SimpleCommandBus.builder().build();
        commandBus = spy(commandBus);
        mockRepository = mock(Repository.class);
        when(mockRepository.newInstance(any())).thenAnswer(
                invocation -> AnnotatedAggregate.initialize(
                        (Callable<AggregateWithUUID>) invocation.getArguments()[0],
                        aggregateModel,
                        mock(EventBus.class)
                ));

        ParameterResolverFactory parameterResolverFactory =
                ClasspathParameterResolverFactory.forClass(AggregateWithUUID.class);

        aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(AggregateWithUUID.class,
                                                                             parameterResolverFactory);
        testSubject = AggregateAnnotationCommandHandler.<AggregateWithUUID>builder()
                .aggregateType(AggregateWithUUID.class)
                .parameterResolverFactory(parameterResolverFactory)
                .repository(mockRepository)
                .build();
        testSubject.subscribe(commandBus);
    }

    @Test
    void testSupportedCommands() {
        Set<String> actual = testSubject.supportedCommandNames();
        Set<String> expected = new HashSet<>(Arrays.asList(
                AggregateWithUUIDCmd.class.getName()
        ));

        assertEquals(expected, actual);
    }

    @Test
    public void testCommandHandlerCreatesOrUpdatesAggregateInstance() throws Exception {

        final CommandCallback callback = spy(LoggingCallback.INSTANCE);
        final CommandMessage<Object> message = asCommandMessage(new AggregateWithUUIDCmd(UUID.randomUUID()));
        when(mockRepository.loadOrCreate(anyString(), any()))
                .thenReturn(createAggregate(new AggregateWithUUID()));
        commandBus.dispatch(message, callback);
        verify(mockRepository).loadOrCreate(anyString(), any());
        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
                .forClass(CommandResultMessage.class);
        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
        assertEquals(message, commandCaptor.getValue());
        assertEquals("Create or update works fine", responseCaptor.getValue().getPayload());
    }

    AnnotatedAggregate<AggregateWithUUID> createAggregate(AggregateWithUUID root) {
        return AnnotatedAggregate.initialize(root, aggregateModel, null);
    }

    private static class AggregateWithUUID {

        @AggregateIdentifier
        private UUID id;

        public AggregateWithUUID() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
        public String handle(AggregateWithUUIDCmd cmd) {
            this.id = cmd.id;
            return "Create or update works fine";
        }
    }

    private static class AggregateWithUUIDCmd {

        @TargetAggregateIdentifier
        private final UUID id;

        private AggregateWithUUIDCmd(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }
    }
}
