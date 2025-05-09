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

package org.axonframework.modelling.entity;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStreamTestUtils;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.ManagedEntity;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityCommandHandlingComponentTest {

    @Mock
    private Repository.LifecycleManagement<String, TestEntity> repository;

    @Mock
    private EntityModel<TestEntity> entityModel;

    @Mock
    private EntityIdResolver<String> idResolver;

    private final CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(Integer.class), "command");
    private final String entityId = "myEntityId456";
    private final ManagedEntity<String, TestEntity> managedEntity = mock(ManagedEntity.class);
    private final TestEntity mockEntity = mock(TestEntity.class);
    private EntityCreationPolicy creationPolicy = EntityCreationPolicy.CREATE_IF_MISSING;

    private EntityCommandHandlingComponent<String, TestEntity> testComponent;

    @BeforeEach
    void setUp() {
        testComponent = new EntityCommandHandlingComponent<>(
                repository,
                entityModel,
                idResolver,
                cm -> creationPolicy
        );
        lenient().when(idResolver.resolve(eq(commandMessage), any())).thenReturn(entityId);
        lenient().when(repository.load(eq(entityId), any()))
                 .thenReturn(CompletableFuture.completedFuture(managedEntity));

        CommandResultMessage<?> instanceResultMessage = new GenericCommandResultMessage<>(new MessageType(String.class),
                                                                                          "instance");
        MessageStream.Single<CommandResultMessage<?>> instanceResult = MessageStream.just(instanceResultMessage);
        CommandResultMessage<?> creationalResultMessage = new GenericCommandResultMessage<>(new MessageType(String.class),
                                                                                            "creational");
        MessageStream.Single<CommandResultMessage<?>> creationalResult = MessageStream.just(creationalResultMessage);
        lenient().when(entityModel.handleInstance(eq(commandMessage), eq(mockEntity), any()))
                 .thenReturn(instanceResult);
        lenient().when(entityModel.handleCreate(any(), any())).thenReturn(creationalResult);
    }

    @Test
    void returnsQualifiedNamesFromEntityModel() {
        Set<QualifiedName> supportedCommands = Set.of(
                new QualifiedName("command1"),
                new QualifiedName("command3"),
                new QualifiedName("command5")
        );
        when(entityModel.supportedCommands()).thenReturn(supportedCommands);

        Set<QualifiedName> result = testComponent.supportedCommands();
        Assertions.assertEquals(supportedCommands, result);
    }

    @Nested
    class CreateIfMissing {

        @BeforeEach
        void setUpPolicy() {
            creationPolicy = EntityCreationPolicy.CREATE_IF_MISSING;
        }

        @Test
        void willInvokeCreationalCommandHandlerWhenRepositoryReturnsNull() {
            when(managedEntity.entity()).thenReturn(null);

            MessageStream.Single<CommandResultMessage<?>> componentResult = testComponent.handle(
                    commandMessage, mock(ProcessingContext.class));
            verify(entityModel).handleCreate(eq(commandMessage), any());
            assertEquals("creational", componentResult.first().asCompletableFuture().join().message().getPayload());
        }

        @Test
        void willInvokeInstanceCommandHandlerWhenRepositoryReturnsEntity() {
            when(managedEntity.entity()).thenReturn(mockEntity);

            MessageStream.Single<CommandResultMessage<?>> componentResult = testComponent.handle(
                    commandMessage, mock(ProcessingContext.class));

            verify(entityModel).handleInstance(eq(commandMessage), eq(mockEntity), any());
            Assertions.assertEquals("instance", componentResult.asCompletableFuture().join().message().getPayload());
        }
    }

    @Nested
    class Always {

        @BeforeEach
        void setUpPolicy() {
            creationPolicy = EntityCreationPolicy.ALWAYS;
        }

        @Test
        void willNotTryToLoadEntityAndAlwaysCallCreationalHandler() {

            MessageStream.Single<CommandResultMessage<?>> componentResult = testComponent.handle(
                    commandMessage, mock(ProcessingContext.class));

            verify(entityModel).handleCreate(eq(commandMessage), any());
            verifyNoInteractions(repository);
            assertEquals("creational", componentResult.asCompletableFuture().join().message().getPayload());
        }
    }

    @Nested
    class Never {

        @BeforeEach
        void setUpPolicy() {
            creationPolicy = EntityCreationPolicy.NEVER;
        }

        @Test
        void willThrowExceptionIfEntityDoesNotExist() {
            when(managedEntity.entity()).thenReturn(null);

            MessageStream.Single<CommandResultMessage<?>> componentResult = testComponent.handle(
                    commandMessage, mock(ProcessingContext.class));

            MessageStreamTestUtils.assertCompletedExceptionally(componentResult,
                                                                IllegalStateException.class,
            "No entity found for command [java.lang.Integer] with id [" + entityId + "]"
            );
        }

        @Test
        void willCallInstanceCommandHandlerWhenEntityExists() {
            when(managedEntity.entity()).thenReturn(mockEntity);

            MessageStream.Single<CommandResultMessage<?>> componentResult = testComponent.handle(
                    commandMessage, mock(ProcessingContext.class));

            verify(entityModel).handleInstance(eq(commandMessage), eq(mockEntity), any());
            Assertions.assertEquals("instance", componentResult.asCompletableFuture().join().message().getPayload());
        }
    }

    @Test
    void failureToResolveIdWillResultInFailedMessageStream() {
        when(idResolver.resolve(eq(commandMessage), any())).thenThrow(new RuntimeException("Failed to resolve ID"));


        MessageStream.Single<? extends CommandResultMessage<?>> componentResult = testComponent.handle(commandMessage,
                                                                                                       mock(ProcessingContext.class));

        var exception = assertThrows(RuntimeException.class, () -> componentResult.asCompletableFuture().join());
        assertEquals("Failed to resolve ID", exception.getCause().getMessage());
    }

    @Test
    void failureToLoadEntityWillResultInFailedMessageStream() {
        when(idResolver.resolve(eq(commandMessage), any())).thenReturn(entityId);
        when(managedEntity.entity()).thenReturn(mockEntity);
        when(repository.load(eq(entityId), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException(
                "Failed to load entity")));


        MessageStream.Single<? extends CommandResultMessage<?>> componentResult = testComponent.handle(commandMessage,
                                                                                                       mock(ProcessingContext.class));

        var exception = assertThrows(RuntimeException.class, () -> componentResult.asCompletableFuture().join());
        assertEquals("Failed to load entity", exception.getCause().getMessage());
    }

    @Test
    void correctlyDescribesComponent() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        testComponent.describeTo(descriptor);

        assertEquals(repository, descriptor.getProperty("repository"));
        assertEquals(entityModel, descriptor.getProperty("entityModel"));
        assertEquals(idResolver, descriptor.getProperty("idResolver"));
    }


    private static class TestEntity {
        // Test entity class for generic type resolution
    }
}